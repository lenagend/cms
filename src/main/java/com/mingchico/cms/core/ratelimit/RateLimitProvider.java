package com.mingchico.cms.core.ratelimit;

import io.github.bucket4j.ConsumptionProbe;

/**
 * <h3>[Rate Limit 핵심 인터페이스]</h3>
 * <p>
 * 다양한 처리율 제한(Rate Limit) 알고리즘 및 저장소 방식에 대한 공통 규격을 정의합니다.
 * 로컬 메모리 방식({@code Caffeine})이나 분산 환경 방식({@code Redis}) 등 구체적인 구현체에 관계없이
 * 일관된 인터페이스를 제공합니다.
 * </p>
 *
 * <h3>주요 설계 원칙</h3>
 * <ul>
 * <li><b>의존성 역전 원칙 (DIP):</b> {@code GlobalRateLimitFilter}는 구체적인 구현 기술이 아닌
 * 이 인터페이스에 의존하여, 저장소 변경 시에도 비즈니스 로직에 영향을 주지 않습니다.</li>
 * <li><b>높은 확장성:</b> 새로운 제한 알고리즘이나 차세대 저장소가 도입되어도 기존 클라이언트(Filter)
 * 코드의 수정 없이 기능을 확장할 수 있습니다.</li>
 * <li><b>전략 패턴 (Strategy Pattern):</b> 런타임에 설정값에 따라 적절한 제한 전략을 선택할 수 있는 구조를 제공합니다.</li>
 * </ul>
 *
 * @see com.mingchico.cms.core.ratelimit.GlobalRateLimitFilter
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
