package com.mingchico.cms.core.ratelimit;

import io.github.bucket4j.ConsumptionProbe;

/**
 * <h3>[Rate Limit 핵심 인터페이스]</h3>
 * <p>
 * 다양한 처리율 제한(Rate Limit) 알고리즘 및 저장소 방식에 대한 공통 규격을 정의합니다.
 * 테넌트별 동적 할당을 지원하기 위해 용량(Capacity)을 파라미터로 받습니다.
 * </p>
 */
public interface RateLimitProvider {
    /**
     * 특정 키(IP, Tenant 등)에 대해 토큰 소모를 시도합니다.
     *
     * @param key      클라이언트 식별자 (TenantID:IP 등)
     * @param capacity 해당 버킷의 최대 허용량 (테넌트별로 다르게 설정 가능)
     * @return ConsumptionProbe - 남은 토큰 수, 차단 시 대기해야 할 시간 등
     */
    ConsumptionProbe tryConsume(String key, int capacity);
}