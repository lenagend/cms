package com.mingchico.cms.core.tenant;

import jakarta.servlet.http.HttpServletRequest;

/**
 * <h3>[테넌트 리졸버 인터페이스]</h3>
 * <p>
 * HTTP 요청으로부터 "이 요청이 어떤 사이트(Tenant)를 향한 것인가?"를 식별하는 전략을 정의합니다.
 * </p>
 */
public interface TenantResolver {

    /**
     * 요청 정보를 분석하여 사이트 코드를 반환합니다.
     *
     * @param request HTTP 요청 객체
     * @return 식별된 사이트 코드 (예: "SITE_MAIN")
     * @throws UnknownTenantException 등록되지 않은 도메인일 경우 발생
     */
    String resolveSiteCode(HttpServletRequest request);

    /**
     * 식별 실패 시 발생하는 예외
     */
    class UnknownTenantException extends RuntimeException {
        public UnknownTenantException(String message) {
            super(message);
        }
    }
}