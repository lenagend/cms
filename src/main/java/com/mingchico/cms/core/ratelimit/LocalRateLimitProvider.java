package com.mingchico.cms.core.ratelimit;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cms.ratelimit.mode", havingValue = "local", matchIfMissing = true)
public class LocalRateLimitProvider implements RateLimitProvider {

    private final RateLimitProperties properties;
    private Cache<String, Bucket> cache;

    @PostConstruct
    public void init() {
        // Caffeine Cache (카페인 캐시): 고성능 로컬 메모리 캐시.
        // (설명: 1시간 동안 활동 없는 IP는 메모리에서 자동 삭제하여 OOM을 방지합니다.)
        this.cache = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(Duration.ofHours(1))
                .build();
        
        log.info("RateLimit: Local Mode Activated. (Base Limit: {}, Servers: {})", 
                properties.getCapacity(), properties.getServerCount());
    }

    @Override
    public ConsumptionProbe tryConsume(String key) {
        return cache.get(key, this::createNewBucket).tryConsumeAndReturnRemaining(1);
    }

    private Bucket createNewBucket(String key) {
        // [스마트 스케일링] 서버가 여러 대면, 한 서버가 감당할 몫을 1/N로 줄입니다.
        // 예: 총 100회 제한, 서버 4대 -> 각 서버는 25회씩만 허용.
        int adjustedCapacity = Math.max(1, properties.getCapacity() / properties.getServerCount());
        
        Bandwidth limit = Bandwidth.classic(adjustedCapacity, 
                Refill.greedy(adjustedCapacity, Duration.ofMinutes(1)));
        
        return Bucket.builder().addLimit(limit).build();
    }
}