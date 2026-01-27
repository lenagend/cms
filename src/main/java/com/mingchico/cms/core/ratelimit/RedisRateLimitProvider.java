package com.mingchico.cms.core.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
// 설정 파일의 mode가 'redis'일 때만 이 빈(Bean)을 생성함
@ConditionalOnProperty(name = "cms.ratelimit.mode", havingValue = "redis")
public class RedisRateLimitProvider implements RateLimitProvider {

    private final RateLimitProperties properties;
    private LettuceBasedProxyManager<String> proxyManager;

    // Redis 클라이언트 및 연결 객체 (수동 리소스 해제를 위해 보관)
    private RedisClient redisClient;
    private StatefulRedisConnection<String, byte[]> connection;

    @PostConstruct
    public void init() {
        // 1. Redis 연결을 위한 URI 생성 및 클라이언트 초기화
        this.redisClient = RedisClient.create(RedisURI.builder()
                .withHost(properties.getRedisHost())
                .withPort(properties.getRedisPort())
                .build());

        // 2. Redis 전송 데이터 코덱 설정 (Key: String, Value: byte[]) 및 연결 확립
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        this.connection = redisClient.connect(codec);

        // 3. Bucket4j Redis Proxy Manager 설정: 분산 환경에서의 버킷 관리 및 자동 삭제(TTL) 전략 수립
        this.proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(1)))
                .build();

        log.info("RateLimit: Redis Distributed Mode Activated.");
    }

    /**
     * 어플리케이션 종료 시 호출되어 Redis 연결을 안전하게 닫음 (메모리 및 포트 점유 방지)
     */
    @PreDestroy
    public void shutdown() {
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
        log.info("RateLimit: Redis resources have been released.");
    }

    @Override
    public ConsumptionProbe tryConsume(String key) {
        // 4. 버킷 정책 정의: 분당 'capacity' 만큼의 요청 허용 (Token Bucket 알고리즘)
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(properties.getCapacity())
                        .refillGreedy(properties.getCapacity(), Duration.ofMinutes(1))
                        .build())
                .build();

        // 5. Redis에서 key(IP 등)에 해당하는 버킷을 가져와 토큰 1개를 소모하고 결과(남은 토큰 등) 반환
        return proxyManager.builder().build(key, () -> configuration).tryConsumeAndReturnRemaining(1);
    }
}