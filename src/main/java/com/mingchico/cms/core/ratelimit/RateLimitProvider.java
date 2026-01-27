package com.mingchico.cms.core.ratelimit;

import io.github.bucket4j.ConsumptionProbe;

public interface RateLimitProvider {
    /**
     * IP(key)에 대해 토큰 소모를 시도합니다.
     * @param key 클라이언트 IP
     * @return 남은 토큰 정보 및 대기 시간
     */
    ConsumptionProbe tryConsume(String key);
}