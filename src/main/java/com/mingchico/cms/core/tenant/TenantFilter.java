package com.mingchico.cms.core.tenant;

import com.mingchico.cms.core.tenant.dto.TenantInfo;
import com.mingchico.cms.core.tenant.service.TenantMetadataProvider;
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
 * 또한, 사이트 상태(점검중 등)에 따른 접근 제어를 수행합니다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class TenantFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;
    private final TenantMetadataProvider tenantMetadataProvider;
    private final TenantProperties tenantProperties;

    private static final String MDC_SITE_KEY = "siteCode";
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : tenantProperties.getExcludedPaths()) {
            if (pathMatcher.match(pattern, path)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            // 1. 사이트 코드 식별
            String siteCode = tenantResolver.resolveSiteCode(request);

            // 2. 메타데이터 로딩 (Cache)
            TenantInfo tenantInfo = tenantMetadataProvider.getTenantInfo(siteCode);

            // 3. [정책 검사] 유지보수 모드 차단
            // TODO: 관리자 IP나 특정 헤더가 있는 경우 통과시키는 화이트리스트 로직 추가 권장
            if (tenantInfo.maintenance()) {
                log.warn("⛔ Access Blocked (Maintenance Mode): {}", siteCode);
                sendErrorResponse(response, HttpStatus.SERVICE_UNAVAILABLE, "시스템 점검 중입니다.");
                return; // 필터 체인 중단
            }

            // 4. 컨텍스트 바인딩
            TenantContext.setContext(tenantInfo);
            MDC.put(MDC_SITE_KEY, siteCode);

            filterChain.doFilter(request, response);

        } catch (TenantResolver.UnknownTenantException e) {
            log.warn("⛔ Access Rejected: {}", e.getMessage());
            sendErrorResponse(response, HttpStatus.NOT_FOUND, "존재하지 않는 사이트입니다.");
        } finally {
            // 5. 스레드 로컬 정리 (매우 중요: 스레드 풀 오염 방지)
            TenantContext.clear();
            MDC.remove(MDC_SITE_KEY);
        }
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\": \"" + status.getReasonPhrase() + "\", \"message\": \"" + message + "\"}");
    }
}