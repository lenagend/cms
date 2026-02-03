package com.mingchico.cms.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * <h3>[캐시 통합 설정 프로퍼티]</h3>
 * <p>
 * {@code application.yml}의 'cms.cache' 섹션을 매핑합니다.
 * 시스템의 모든 캐시 정책(TTL, Size 등)을 중앙에서 관리합니다.
 * </p>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cms.cache")
public class CacheProperties {

    /**
     * [캐시 운영 모드]
     * 시스템 전반에서 사용할 캐시 엔진의 종류를 결정합니다. (기본값: LOCAL)
     * - LOCAL: Caffeine (단일 서버 메모리 기반, 초고속)
     * - REDIS: Redis (분산 서버 간 데이터 공유 가능)
     */
    private Mode mode = Mode.LOCAL;

    /**
     * 기본 캐시 정책 (명시되지 않은 캐시에 적용)
     */
    private Policy defaultPolicy = new Policy();

    /**
     * 캐시 이름별 상세 정책
     * Key: 캐시 이름 (예: "tenant_meta")
     * Value: 정책 설정
     */
    private Map<String, Policy> policies = new HashMap<>();

    /**
     * 캐시 저장소 모드 정의
     */
    public enum Mode { LOCAL, REDIS }

    @Getter
    @Setter
    public static class Policy {
        /** 만료 시간 (기본값: 10분) */
        private Duration ttl = Duration.ofMinutes(10);

        /** 최대 항목 수 (기본값: 1000) */
        private long maxSize = 1000;
    }
}