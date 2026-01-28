package com.mingchico.cms.core.ratelimit;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * [글로벌 Rate Limit 필터]
 * 시스템으로 들어오는 모든 HTTP 요청을 가로채어 트래픽 양을 제어합니다.
 *
 * <p>주요 특징:</p>
 * <ul>
 * <li><b>최우선 순위 실행:</b> Spring Security보다 먼저 실행되어 악성 트래픽이 인증 로직(DB 조회 등)을 태우기도 전에 차단합니다.</li>
 * <li><b>Fail-Open 정책:</b> Rate Limit 시스템(Redis 등)에 장애가 발생하면 차단하지 않고 통과시켜 서비스 가용성을 보장합니다.</li>
 * <li><b>지능적 필터링:</b> 정적 리소스나 CORS 사전 요청(OPTIONS)은 제한 대상에서 제외하여 불필요한 카운팅을 막습니다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
// [순서 설정: HIGHEST_PRECEDENCE]
// 가장 높은 우선순위(Integer.MIN_VALUE)를 가집니다.
// 장점: DDoS성 공격이 들어올 때 비즈니스 로직이나 DB 커넥션을 점유하기 전에 즉시 쳐낼 수 있습니다.
@Order(Ordered.HIGHEST_PRECEDENCE)
// 설정 파일에서 cms.security.rate-limit.enabled=true 일 때만 활성화 (기본값 true)
@ConditionalOnProperty(name = "cms.security.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProvider rateLimitProvider;
    private final JsonMapper jsonMapper;

    /**
     * 신뢰할 수 있는 IP 헤더 목록 (우선순위 순)
     * 클라우드(AWS ALB, Cloudflare)나 L4 스위치 등을 거칠 때 원본 IP가 담기는 헤더들입니다.
     */
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    /**
     * Rate Limit 제외 대상 확장자 목록 (정적 리소스)
     * 이 파일들은 서버 부하가 적으므로 굳이 트래픽 제한을 걸어 사용자 경험을 해칠 필요가 없습니다.
     */
    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".woff", ".woff2", ".ttf"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // [1. 필터링 제외 대상 확인]
        // Rate Limit을 적용하면 안 되는 요청(CORS, 정적 파일 등)은 즉시 통과시킵니다.
        if (shouldSkipRateLimit(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // [2. 클라이언트 IP 추출]
        // 다양한 프록시 환경을 고려하여 최대한 정확한 실제 IP를 찾아냅니다.
        String clientIp = resolveClientIp(request);

        try {
            // [3. 토큰 소모 시도 (핵심 로직)]
            ConsumptionProbe probe = rateLimitProvider.tryConsume(clientIp);

            if (probe.isConsumed()) {
                // [성공 - 통과]
                // 클라이언트에게 "앞으로 몇 번 더 요청할 수 있는지" 친절하게 알려줍니다.
                response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
                filterChain.doFilter(request, response);
            } else {
                // [실패 - 차단]
                // 허용량을 초과했습니다. 429 에러를 반환합니다.
                long waitForRefillSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
                log.warn("Rate Limit Exceeded for IP: {} (Wait: {}s)", clientIp, waitForRefillSeconds);
                handleRateLimitExceeded(response, waitForRefillSeconds);
            }
        } catch (Exception e) {
            // [4. Fail-Open (장애 격리)]
            // Redis 연결 실패 등 Rate Limit 내부 오류가 발생해도,
            // 실제 비즈니스 서비스는 중단되지 않고 돌아가야 합니다. (보안 < 가용성)
            log.error("Rate Limit System Error (Bypassing for IP: {}): {}", clientIp, e.getMessage());
            filterChain.doFilter(request, response);
        }
    }

    /**
     * [제외 조건 판단 로직]
     * Rate Limit 카운팅을 하지 말아야 할 요청인지 검사합니다.
     */
    private boolean shouldSkipRateLimit(HttpServletRequest request) {
        // 1. CORS Preflight 요청 (OPTIONS)
        // 브라우저가 실제 요청을 보내기 전에 안전한지 찔러보는 요청입니다. 차단하면 프론트엔드 에러가 납니다.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 2. 정적 리소스 (Static Resources)
        // 이미지나 스크립트 파일 요청까지 카운팅하면 페이지 하나 로딩에 수십 개 토큰이 소모될 수 있습니다.
        String path = request.getRequestURI().toLowerCase();
        // 확장자로 끝나는지 확인 (예: /assets/logo.png)
        return STATIC_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    /**
     * [방어적 IP 추출 로직]
     * 프록시 헤더(X-Forwarded-For)를 파싱하여 원본 IP를 찾습니다.
     * <p>
     * 주의: X-Forwarded-For 헤더는 클라이언트가 조작할 수 있습니다.
     * 완벽한 보안을 위해서는 앞단(Nginx, AWS ALB)에서 이 헤더를 강제로 덮어씌우는 설정이 병행되어야 합니다.
     * </p>
     */
    private String resolveClientIp(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 형식: "client_ip, proxy1_ip, proxy2_ip"
                // 콤마(,)로 구분된 경우 맨 왼쪽(첫 번째) IP가 원본 클라이언트입니다.
                if (ip.contains(",")) {
                    ip = ip.split(",")[0];
                }
                return ip.trim();
            }
        }
        // 헤더가 없으면 실제 TCP 연결된 Remote IP를 반환합니다.
        return request.getRemoteAddr();
    }

    /**
     * [차단 응답 처리]
     * 429 Too Many Requests 상태 코드와 함께 JSON 포맷으로 에러 메시지를 반환합니다.
     */
    private void handleRateLimitExceeded(HttpServletResponse response, long waitForRefill) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        // 표준 헤더: 언제 다시 시도하면 되는지 초 단위로 알려줌
        response.setHeader("Retry-After", String.valueOf(waitForRefill));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", "Too Many Requests");
        body.put("message", String.format("요청이 너무 많습니다. %d초 후에 다시 시도해주세요.", waitForRefill));
        body.put("wait_seconds", waitForRefill);

        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
