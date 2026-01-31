package com.mingchico.cms.core.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mingchico.cms.core.tenant.TenantContext;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * [글로벌 Rate Limit 필터]
 * <p>
 * 시스템으로 들어오는 모든 HTTP 요청을 가장 먼저 가로채어 트래픽 양을 제어하는 문지기(Gatekeeper) 역할을 합니다.
 * Spring Security보다 앞단에서 동작하여 악성 트래픽이 서버 리소스를 점유하기 전에 차단합니다.
 * </p>
 *
 * <h3>핵심 기능</h3>
 * <ul>
 * <li><b>최우선 순위 방어:</b> 인증/인가 로직이 돌기도 전에 실행되어 DDoS 공격 등으로부터 DB와 애플리케이션을 보호합니다.</li>
 * <li><b>IP 스푸핑 방지:</b> 헤더 조작을 통한 우회 공격을 막기 위해 신뢰할 수 있는 프록시(L4, Cloudflare 등)만 검증합니다.</li>
 * <li><b>Fail-Open 정책:</b> Rate Limit 시스템(Redis 등)에 장애가 나면, 사용자 요청을 차단하는 대신 통과시켜 서비스 가용성을 최우선으로 합니다.</li>
 * </ul>
 */
@Slf4j
@Component
// [순서 설정: 최우선]
// Integer.MIN_VALUE로 설정되어 모든 필터 중 가장 먼저 실행됩니다.
// 하지만 MDC LOGGING이 더 먼저실행돼야합니다 +1
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
// 설정 파일(application.yml)에서 'cms.security.rate-limit.enabled=true'일 때만 활성화됩니다.
@ConditionalOnProperty(name = "cms.security.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProvider rateLimitProvider;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * [신뢰할 수 있는 IP 매처 목록]
     * 매 요청마다 CIDR(예: 192.168.0.0/16)을 파싱하면 성능이 떨어지므로,
     * 필터 생성 시점에 미리 컴파일(Compile)하여 메모리에 올려둡니다.
     */
    private final List<IpAddressMatcher> trustedIpMatchers;

    /**
     * [클라이언트 IP 헤더 목록]
     * 프록시나 로드밸런서를 거쳐 들어온 요청의 원본 IP가 담기는 헤더들입니다.
     */
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
    };

    public GlobalRateLimitFilter(RateLimitProvider rateLimitProvider,
                                 RateLimitProperties properties,
                                 ObjectMapper objectMapper) {
        this.rateLimitProvider = rateLimitProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;

        // properties에서 문자열로 된 IP 목록을 가져와서, 검증 가능한 Matcher 객체로 변환합니다.
        // 예: "127.0.0.1" -> IpAddressMatcher 객체
        this.trustedIpMatchers = properties.getTrustedProxies().stream()
                .map(IpAddressMatcher::new)
                .collect(Collectors.toList());
    }

    /**
     * [필터 로직 수행]
     * 실제 요청이 들어왔을 때 실행되는 메인 메서드입니다.
     *
     * @param request     HTTP 요청 객체 (NonNull: 절대 null이 아님을 보장)
     * @param response    HTTP 응답 객체 (NonNull)
     * @param filterChain 다음 필터로 넘겨주는 체인 (NonNull)
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (shouldSkipRateLimit(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. 키 생성 (Tenant Isolation)
        // TenantFilter가 앞단에서 채워준 Context를 활용합니다.
        String siteCode = TenantContext.getSiteCode();
        if (!StringUtils.hasText(siteCode)) {
            siteCode = "anonymous"; // 테넌트 식별 실패 시(예: 제외 경로) 기본값
        }

        String clientIp = resolveClientIp(request);

        // [Key 구조] siteCode:clientIp:uri
        // 예: "shop_a:127.0.0.1:/api/login"
        String rateLimitKey = siteCode + ":" + clientIp + ":" + request.getRequestURI();

        // 2. 용량 결정 (Dynamic Capacity)
        // 설정 맵에서 siteCode로 조회해보고, 없으면 기본 capacity(100) 사용
        int capacity = properties.getPerTenantCapacities()
                .getOrDefault(siteCode, properties.getCapacity());

        try {
            // 3. 토큰 소모 시도
            ConsumptionProbe probe = rateLimitProvider.tryConsume(rateLimitKey, capacity);

            if (probe.isConsumed()) {
                response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
                filterChain.doFilter(request, response);
            } else {
                long waitForRefillSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
                log.warn("Rate Limit Exceeded for Tenant[{}]: IP={}", siteCode, clientIp);
                handleRateLimitExceeded(response, waitForRefillSeconds);
            }
        } catch (Exception e) {
            log.error("Rate Limit System Error (Bypassing): {}", e.getMessage());
            filterChain.doFilter(request, response);
        }
    }

    /**
     * [제외 조건 판단]
     * Rate Limit 카운팅을 하지 말아야 할 요청인지 검사합니다.
     * - OPTIONS 요청: 브라우저의 CORS 사전 검사 (안전함)
     * - 정적 리소스: 이미지, CSS 등 (서버 부하가 적음)
     */
    private boolean shouldSkipRateLimit(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().toLowerCase();

        if (properties.getExcludedPaths().stream().anyMatch(path::startsWith)) {
            return true;
        }

        // 설정 파일(properties)에 정의된 확장자로 끝나는지 확인
        return properties.getExcludedExtensions().stream().anyMatch(path::endsWith);
    }

    /**
     * [보안 강화된 IP 추출 로직]
     * "X-Forwarded-For" 헤더는 클라이언트가 마음대로 조작할 수 있어 위험합니다.
     * 따라서 요청이 '신뢰할 수 있는 서버(로드밸런서 등)'에서 왔을 때만 해당 헤더를 믿습니다.
     *
     * @return 검증된 실제 클라이언트 IP
     */
    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr(); // 실제 TCP 연결 IP

        // 1. 요청을 보낸 직전 서버(remoteAddr)가 우리 내부망/로드밸런서(Trusted Proxy)인지 확인
        boolean isTrusted = trustedIpMatchers.stream()
                .anyMatch(matcher -> matcher.matches(request));

        // 2. 신뢰할 수 없는 소스(해커가 직접 요청 등)라면, 헤더는 조작되었을 가능성이 높으므로 무시
        if (!isTrusted) {
            return remoteAddr;
        }

        // 3. 신뢰할 수 있는 경로라면 X-Forwarded-For 등을 파싱하여 원본 IP 추출
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                // "client, proxy1, proxy2" 형식일 경우 맨 앞이 원본 클라이언트
                if (ip.contains(",")) {
                    ip = ip.split(",")[0];
                }
                return ip.trim();
            }
        }
        return remoteAddr;
    }

    /**
     * [차단 응답 처리 (429 Too Many Requests)]
     * 단순히 에러만 뱉는 게 아니라, JSON 포맷으로 "왜 차단됐는지", "언제 풀리는지" 친절하게 알려줍니다.
     */
    private void handleRateLimitExceeded(HttpServletResponse response, long waitForRefill) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        // 표준 헤더: Retry-After (초 단위)
        response.setHeader("Retry-After", String.valueOf(waitForRefill));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", "Too Many Requests");
        body.put("message", String.format("요청이 너무 많습니다. %d초 후에 다시 시도해주세요.", waitForRefill));
        body.put("wait_seconds", waitForRefill);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}