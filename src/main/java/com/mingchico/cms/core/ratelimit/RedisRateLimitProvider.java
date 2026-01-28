package com.mingchico.cms.core.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.spring.cas.SpringDataRedisBasedProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.ByteArrayRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * [Redis 기반 분산 Rate Limit 구현체]
 * 여러 대의 서버가 운영되는 클러스터 환경에서 공통된 Redis 저장소를 통해 통합 트래픽을 제어합니다.
 *
 * 주요 특징:
 * - 데이터 정합성: 모든 서버 인스턴스가 하나의 IP에 대해 동일한 잔여 토큰 정보를 공유합니다.
 * - 원자적 연산: Bucket4j와 Redis의 결합을 통해 다중 스레드 환경에서도 정확한 카운팅을 보장합니다.
 * - 리소스 최적화: Supplier 패턴을 사용하여 설정 객체를 재사용하며, 만료 전략을 통해 Redis 메모리를 효율적으로 관리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cms.security.rate-limit.mode", havingValue = "REDIS")
public class RedisRateLimitProvider implements RateLimitProvider {

    private final RateLimitProperties properties;
    private final RedisConnectionFactory redisConnectionFactory;

    /**
     * ProxyManager: Redis와 Bucket4j를 연결하여 분산 환경에서의 버킷 제어를 담당하는 관리자입니다.
     */
    private ProxyManager<String> proxyManager;

    /**
     * [성능 최적화] BucketConfiguration Supplier
     * 매번 빌더를 통해 새로운 설정 객체를 생성하는 대신, 불변(Immutable)인 설정을 미리 메모리에 올려두고 재사용합니다.
     * 이를 통해 CPU 연산과 메모리 할당(GC) 효율을 극대화합니다.
     */
    private Supplier<BucketConfiguration> bucketConfigSupplier;

    @PostConstruct
    public void init() {
        // [RedisTemplate 수동 구성]
        // Bucket4j는 내부적으로 바이너리 데이터를 주고받으므로,
        // 데이터 정합성을 위해 ValueSerializer를 ByteArrayRedisSerializer로 설정한 전용 템플릿이 필요합니다.
        RedisTemplate<String, byte[]> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new ByteArrayRedisSerializer());
        redisTemplate.afterPropertiesSet();

        // [ProxyManager 초기화]
        // SpringDataRedisBasedProxyManager를 사용하여 Spring의 Redis 인프라와 Bucket4j를 연동합니다.
        this.proxyManager = SpringDataRedisBasedProxyManager.builderFor(redisTemplate)
                .withExpirationStrategy(
                        // 리필이 완료된 버킷 정보가 Redis 메모리를 계속 점유하지 않도록 1시간 후 만료 처리합니다.
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(1))
                )
                .build();

        // [설정 공급자(Supplier) 초기화]
        // 실제 버킷이 생성될 때 참조할 규칙을 정의합니다.
        // 람다 식 내부에서 properties를 참조하므로 설정값이 변경되면 애플리케이션 재시작 시 반영됩니다.
        this.bucketConfigSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(properties.getCapacity())
                        .refillGreedy(properties.getCapacity(), Duration.ofMinutes(1))
                        .build())
                .build();

        log.info("RateLimit: Redis Distributed Mode Activated. (Capacity: {}/min)", properties.getCapacity());
    }

    @Override
    public ConsumptionProbe tryConsume(String key) {
        // Redis Key 설계: 서비스 내 다른 데이터와의 충돌을 방지하기 위해 'rate_limit:' 접두사를 붙입니다.
        String redisKey = "rate_limit:" + key;

        // proxyManager를 통해 Redis에 저장된 해당 키의 버킷을 가져오거나,
        // 처음 접근하는 IP인 경우 bucketConfigSupplier를 실행하여 새 버킷을 생성합니다.
        return proxyManager.builder()
                .build(redisKey, bucketConfigSupplier)
                .tryConsumeAndReturnRemaining(1);
    }
}
