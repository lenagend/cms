package com.mingchico.cms.core.ratelimit;

import com.mingchico.cms.core.config.CacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * <h3>[Rate Limit 구성 설정]</h3>
 * <p>
 * 중앙 캐시 설정({@link CacheProperties})의 모드(LOCAL/REDIS)에 따라
 * 적절한 {@link RateLimitProvider} 구현체를 선택하여 빈으로 등록합니다.
 * </p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cms.security.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfig {

    private final CacheProperties cacheProperties;
    private final RateLimitProperties rateLimitProperties;

    @Bean
    public RateLimitProvider rateLimitProvider(
            // Redis 모드일 때만 주입되도록 ObjectProvider 등을 쓸 수도 있지만,
            // 보통 Spring Data Redis가 있으면 Factory는 자동 구성되므로 required=false 처리
            @org.springframework.beans.factory.annotation.Autowired(required = false) 
            RedisConnectionFactory redisConnectionFactory
    ) {
        CacheProperties.Mode mode = cacheProperties.getMode();

        if (mode == CacheProperties.Mode.REDIS) {
            if (redisConnectionFactory == null) {
                throw new IllegalStateException("Redis Mode is enabled but RedisConnectionFactory is missing.");
            }
            log.info("🚀 Rate Limit Provider: REDIS (Centralized Config)");
            return new RedisRateLimitProvider(rateLimitProperties, redisConnectionFactory);
        }

        log.info("🏠 Rate Limit Provider: LOCAL (Centralized Config)");
        return new LocalRateLimitProvider(rateLimitProperties);
    }
}