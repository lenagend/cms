package com.mingchico.cms.core.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Map;

/**
 * <h3>[중앙 캐시 매니저 설정]</h3>
 * <p>
 * {@link CacheProperties}의 설정(Mode, TTL, Size)을 기반으로
 * <b>Caffeine(Local)</b> 또는 <b>Redis(Distributed)</b> 캐시 매니저를 동적으로 생성합니다.
 * </p>
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    private final CacheProperties cacheProperties;

    /**
     * [캐시 매니저 통합 빈]
     * Mode 설정에 따라 구현체를 스위칭합니다.
     */
    @Bean
    @Primary
    public CacheManager cacheManager(
            // Redis 모드일 때만 사용되므로 required = false (단, 실제 실행 시엔 Null 체크됨)
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            RedisConnectionFactory redisConnectionFactory
    ) {
        // [핵심 로직] 모드에 따른 분기 처리
        if (cacheProperties.getMode() == CacheProperties.Mode.REDIS) {
            return createRedisCacheManager(redisConnectionFactory);
        }

        // 기본값: LOCAL
        return createCaffeineCacheManager();
    }

    // --- [전략 1] Caffeine (Local) ---
    private CacheManager createCaffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // 1. [기본 설정]
        CacheProperties.Policy defaultPolicy = cacheProperties.getDefaultPolicy();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(defaultPolicy.getTtl())
                .maximumSize(defaultPolicy.getMaxSize())
                .recordStats());

        // 2. [상세 정책 등록] (tenant_meta, i18n_messages 등)
        for (Map.Entry<String, CacheProperties.Policy> entry : cacheProperties.getPolicies().entrySet()) {
            String cacheName = entry.getKey();
            CacheProperties.Policy policy = entry.getValue();

            cacheManager.registerCustomCache(cacheName, Caffeine.newBuilder()
                    .expireAfterWrite(policy.getTtl())
                    .maximumSize(policy.getMaxSize())
                    .recordStats()
                    .build());
        }

        log.info("✅ Cache Strategy: [LOCAL] Caffeine Activated.");
        return cacheManager;
    }

    // --- [전략 2] Redis (Distributed) ---
    private CacheManager createRedisCacheManager(RedisConnectionFactory connectionFactory) {
        if (connectionFactory == null) {
            throw new IllegalStateException("Redis Mode is enabled but RedisConnectionFactory is missing.");
        }

        // 1. [직렬화 설정] JSON 포맷으로 저장하여 가독성 및 호환성 확보
        RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // 2. [기본 설정 적용] (TTL)
        CacheProperties.Policy defaultPolicy = cacheProperties.getDefaultPolicy();
        RedisCacheConfiguration defaultConfig = baseConfig.entryTtl(defaultPolicy.getTtl());

        // 3. [빌더 구성]
        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig);

        // 4. [상세 정책 등록] (tenant_meta 등 별도 TTL 적용)
        for (Map.Entry<String, CacheProperties.Policy> entry : cacheProperties.getPolicies().entrySet()) {
            String cacheName = entry.getKey();
            CacheProperties.Policy policy = entry.getValue();

            builder.withCacheConfiguration(cacheName, baseConfig.entryTtl(policy.getTtl()));
        }

        log.info("✅ Cache Strategy: [REDIS] Distributed Cache Activated.");
        return builder.build();
    }
}