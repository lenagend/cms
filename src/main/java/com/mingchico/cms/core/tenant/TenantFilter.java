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

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        // 관리자 API 경로는 테넌트 식별 없이 통과 (SecurityConfig에서 이미 권한 제어를 하므로 안전함)
        return path.startsWith("/api/admin");
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