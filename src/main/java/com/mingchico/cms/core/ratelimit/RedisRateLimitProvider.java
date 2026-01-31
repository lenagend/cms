package com.mingchico.cms.core.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * [Redis 기반 분산 Rate Limit 제공자]
 * <p>
 * 여러 대의 서버(Scale-out)가 동작하는 환경에서도 트래픽 제한을 정확하게 수행하기 위해
 * Redis를 공용 저장소로 사용하는 구현체입니다.
 * </p>
 *
 * <h3>핵심 원리</h3>
 * <ul>
 * <li><b>Token Bucket 알고리즘:</b> 버킷에 토큰이 일정 속도로 채워지고, 요청이 올 때마다 토큰을 소모합니다.</li>
 * <li><b>분산 환경 동기화:</b> 로컬 메모리가 아닌 Redis에 남은 토큰 수를 저장하므로, A서버와 B서버가 제한량을 공유합니다.</li>
 * <li><b>CAS (Compare-And-Swap):</b> 동시성 이슈(Race Condition)를 해결하기 위해 Redis의 원자적 연산을 사용합니다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
// 설정 파일에서 'cms.security.rate-limit.mode=REDIS'일 때만 빈으로 등록됩니다.
@ConditionalOnProperty(name = "cms.security.rate-limit.mode", havingValue = "REDIS")
public class RedisRateLimitProvider implements RateLimitProvider {

    private static final String KEY_PREFIX = "ratelimit:";

    private final RateLimitProperties properties;
    private final RedisConnectionFactory redisConnectionFactory;
    private StatefulRedisConnection<String, byte[]> connection;


    /**
     * [ProxyManager]
     * Bucket4j 라이브러리와 외부 저장소(Redis) 사이를 연결해주는 중개자입니다.
     * 역할:
     * 1. Redis에서 버킷 정보(남은 토큰 수)를 가져옴
     * 2. 토큰 계산 및 갱신 (트랜잭션 관리)
     * 3. 변경된 정보를 다시 Redis에 저장
     *
     */
    private ProxyManager<String> proxyManager;

    /**
     * [Bucket 설정 Supplier]
     * 매 요청마다 설정을 새로 만들지 않고 재사용하기 위한 공급자(Supplier)입니다.
     * 불변 객체(Immutable)이므로 메모리에 하나만 만들어두고 계속 씁니다.
     */
    private Supplier<BucketConfiguration> bucketConfigSupplier;

    /**
     * [초기화 메서드]
     * 서버 시작 시점에 딱 한 번 실행되어 Redis 연결을 맺고 설정을 마칩니다.
     */
    @PostConstruct
    public void init() {
        // 1. Spring Data Redis에서 순수 Lettuce Client 추출
        RedisClient redisClient = getNativeRedisClient();

        // 2. Bucket4j용 전용 커넥션 생성
        // 성능 최적화를 위해 Key는 String, Value는 byte[]로 직렬화하여 통신합니다.
        this.connection = redisClient
                .connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        // 3. ProxyManager 빌드 (Bucket4j <-> Redis 연결)
        // ClientSideConfig: Redis 클라이언트 측의 설정을 정의합니다. (구 버전의 withExpirationStrategy 대체)
        this.proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .withClientSideConfig(
                        ClientSideConfig.getDefault()
                                // 만료 전략: 버킷이 꽉 차서 더 이상 토큰이 필요 없으면 1시간 뒤 Redis에서 자동 삭제
                                .withExpirationAfterWriteStrategy(
                                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(1))
                                )
                )
                .build();

        // 4. 버킷 설정 정의 (1분에 N개)
        this.bucketConfigSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(properties.getCapacity()) // 버킷의 총 크기
                        // Greedy Refill: 1분마다 한꺼번에 채우지 않고, 물 흐르듯 부드럽게 채움 (예: 60개/1분 -> 1초에 1개씩)
                        .refillGreedy(properties.getCapacity(), Duration.ofMinutes(1))
                        .build())
                .build();

        log.info("RateLimit: Redis Distributed Mode Activated. Capacity: {}/min", properties.getCapacity());
    }

    /**
     * [Native Redis Client 추출]
     * Spring Data Redis가 감싸고 있는 껍데기를 벗겨내고,
     * Bucket4j가 필요로 하는 알맹이(Lettuce RedisClient)를 꺼냅니다.
     *
     * @return io.lettuce.core.RedisClient
     * @throws BeanInitializationException Lettuce 드라이버가 아닐 경우 발생
     */
    private RedisClient getNativeRedisClient() {
        // [Java 16+ Pattern Matching]
        // redisConnectionFactory가 LettuceConnectionFactory 타입인지 검사함과 동시에
        // lettuceFactory 변수로 캐스팅까지 한 번에 수행합니다.
        if (redisConnectionFactory instanceof LettuceConnectionFactory lettuceFactory) {
            RedisClient client = (RedisClient) lettuceFactory.getNativeClient();
            if (client == null) {
                throw new BeanInitializationException("LettuceConnectionFactory returned null for native client.");
            }
            return client;
        }

        // Jedis 등 호환되지 않는 드라이버를 사용할 경우 명확한 에러를 발생시켜 개발자에게 알립니다.
        throw new BeanInitializationException(
                "RedisRateLimitProvider requires LettuceConnectionFactory. Current: " +
                        redisConnectionFactory.getClass().getSimpleName());
    }

    /**
     * [토큰 소모 요청]
     * 클라이언트 IP(key)를 기준으로 Redis에서 토큰 하나를 가져옵니다.
     *
     * @param key 클라이언트 식별자 (IP 주소 등)
     * @return ConsumptionProbe (남은 토큰 수, 대기 시간 등의 결과 정보)
     */
    @Override
    public ConsumptionProbe tryConsume(String key, int capacity) {
        String redisKey = KEY_PREFIX + key;

        // [동적 설정 공급자]
        // 요청된 capacity에 맞춰 버킷 설정을 생성합니다.
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, Duration.ofMinutes(1))
                        .build())
                .build();

        // ProxyManager가 Redis에 키가 없으면 위 설정(supplier)대로 생성하고, 있으면 그대로 사용합니다.
        // 주의: 이미 키가 존재하면(생성된 지 얼마 안 됨) 파라미터로 넘긴 capacity가 무시되고 기존 설정이 유지됩니다.
        // 설정 변경을 즉시 반영하려면 Redis Key를 날려야 합니다.
        return proxyManager.builder()
                .build(redisKey, configSupplier)
                .tryConsumeAndReturnRemaining(1);
    }

    @PreDestroy
    public void shutdown() {
        try {
            connection.close();
        } catch (Exception e) {
            log.warn("Failed to close Redis connection", e);
        }
    }
}