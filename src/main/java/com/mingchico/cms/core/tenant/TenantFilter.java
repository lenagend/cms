package com.mingchico.cms.core.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * <h3>[테넌트 식별 필터]</h3>
 * <p>
 * HTTP 요청의 진입점에서 {@link TenantResolver}를 사용해 사이트 코드를 식별하고,
 * {@link TenantContext}와 MDC(로그)에 주입합니다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
// [순서] MdcLoggingFilter(HIGHEST) -> TenantFilter(Here) -> Security/RateLimit
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class TenantFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;
    private static final String MDC_SITE_KEY = "siteCode";
    private final TenantProperties tenantProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher(); // 패턴 매칭 유틸리티

    /**
     * [필터 제외 로직]
     * yml에 설정된 'excluded-paths'에 포함된 경로는
     * 테넌트 식별 과정을 건너뛰고 다음 필터로 통과시킵니다.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();

        // 1. 프로퍼티에 정의된 제외 경로 확인
        for (String pattern : tenantProperties.getExcludedPaths()) {
            if (pathMatcher.match(pattern, path)) {
                return true; // 필터 실행 안 함 (skip)
            }
        }

        return false; // 필터 실행
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 정적 리소스 등은 테넌트 검사에서 제외할 수도 있으나,
        // 보안상 모든 요청에 대해 주인을 식별하는 것이 안전합니다.

        try {
            // 1. 리졸버를 통해 사이트 코드 획득 (DB+Cache 로직 내부 수행)
            String siteCode = tenantResolver.resolveSiteCode(request);

            // 2. Context 설정
            TenantContext.setSiteCode(siteCode);

            // 3. Logger MDC 설정 (로그에 [siteCode: xxx] 출력됨)
            MDC.put(MDC_SITE_KEY, siteCode);

            filterChain.doFilter(request, response);

        } catch (TenantResolver.UnknownTenantException e) {
            log.warn("Access Rejected: {}", e.getMessage());
            sendErrorResponse(response, e.getMessage());
        } finally {
            // 4. 스레드 로컬 정리 (필수)
            TenantContext.clear();
            MDC.remove(MDC_SITE_KEY);
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\": \"Not Found\", \"message\": \"" + message + "\"}");
    }

}