package com.mingchico.cms.core.ratelimit;

import io.github.bucket4j.ConsumptionProbe;

/**
 * [Rate Limit 핵심 인터페이스]
 * 로컬 메모리 방식(Local)이나 분산 서버 방식(Redis) 등 다양한 구현체에 대한 공통 규격을 정의합니다.
 *
 * 주요 특징:
 * - 의존성 역전(DIP): 필터(GlobalRateLimitFilter)는 구체적인 구현 기술이 아닌 이 인터페이스에 의존합니다.
 * - 확장성: 새로운 제한 알고리즘이나 저장소(예: Database)가 추가되어도 클라이언트 코드를 수정할 필요가 없습니다.
 */
public interface RateLimitProvider {
    /**
     * 특정 키(IP 등)에 대해 토큰 소모를 시도합니다.
     *
     * @param key 클라이언트 식별자 (주로 IP 주소)
     * @return ConsumptionProbe - 남은 토큰 수, 차단 시 대기해야 할 시간 등의 정보를 담고 있는 객체
     */
    ConsumptionProbe tryConsume(String key);
}
