package com.mingchico.cms.core.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * [로컬 기반 Rate Limit 구현체]
 * Caffeine Cache를 사용하여 서버 개별 메모리(Heap) 내에서 트래픽을 제어합니다.
 *
 * 주요 특징:
 * - 고성능/저지연: 네트워크 통신(Redis 등)이 없으므로 응답 속도가 매우 빠릅니다.
 * - 메모리 관리: 최대 저장 용량(100,000개)과 유효 시간(1시간)을 설정하여 OOM(Out Of Memory) 장애를 방지합니다.
 * - 부드러운 리필: Greedy 알고리즘을 사용하여 토큰이 시간에 비례하여 일정하게 채워지도록 관리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
// 'redis' 모드가 아닐 때(local이거나 없을 때) 동작하도록 설정하여 리소스 낭비 방지
@ConditionalOnProperty(name = "cms.security.rate-limit.mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalRateLimitProvider implements RateLimitProvider {

    private final RateLimitProperties properties;
    private Cache<String, Bucket> cache;

    @PostConstruct
    public void init() {
        // [Caffeine Cache 설정]
        // 로컬 메모리(Heap)를 사용하므로 무제한으로 저장하면 OOM(메모리 부족 오류)이 발생하여 서버가 죽을 수 있습니다.
        this.cache = Caffeine.newBuilder()
                // 최대 10만 개의 IP(Key)만 저장. 공격자가 무작위 IP로 공격해도 메모리 한계를 보장함.
                .maximumSize(100_000)
                // 마지막 접근 후 1시간이 지나면 메모리에서 삭제 (불필요한 데이터 정리)
                .expireAfterAccess(Duration.ofHours(1))
                .build();

        log.info("RateLimit: Local Mode Activated. (Limit: {} requests/min per instance)",
                properties.getCapacity());
    }

    @Override
    public ConsumptionProbe tryConsume(String key) {
        // Cache에서 해당 IP의 버킷을 가져오거나, 없으면 새로 생성(createNewBucket)하여 토큰 소모 시도
        return cache.get(key, this::createNewBucket).tryConsumeAndReturnRemaining(1);
    }

    private Bucket createNewBucket(String key) {
        // [알고리즘 설명: Token Bucket]
        // - Capacity: 버킷의 최대 크기 (최대 토큰 수)
        // - Refill: 토큰이 충전되는 속도
        // Greedy 방식: 1분마다 한 번에 채우는 게 아니라, 시간에 비례해서 부드럽게 채워짐 (사용자 경험에 유리)

        Bandwidth limit = Bandwidth.classic(properties.getCapacity(),
                Refill.greedy(properties.getCapacity(), Duration.ofMinutes(1)));

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
