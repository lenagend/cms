package com.mingchico.cms.core.context;

import com.mingchico.cms.core.mdc.MdcLoggingFilter;
import com.mingchico.cms.core.security.CustomUserDetails;
import com.mingchico.cms.core.tenant.TenantContext;
import com.mingchico.cms.core.tenant.dto.TenantInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import org.slf4j.MDC;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

/**
 * <h3>[통합 컨텍스트 홀더 (Context Holder)]</h3>
 * <p>
 * 시스템 전반에 흩어져 있는 <b>요청(Request), 보안(User), 테넌트(Site), 지역(Locale)</b> 정보를
 * 정적 메서드로 손쉽게 조회할 수 있는 파사드(Facade) 유틸리티입니다.
 * </p>
 */
@UtilityClass
public class ContextHolder {

    @Setter
    private static ContextProperties properties;

    // --- [1] Request & Trace Context (요청 추적 및 클라이언트 정보) ---

    public static String getRequestId() {
        // MDC에 값이 없다면 시스템 내부 로직(스케줄러 등)으로 간주
        return Optional.ofNullable(MDC.get(MdcLoggingFilter.MDC_KEY)).orElse("SYSTEM");
    }

    /**
     * 클라이언트의 실제 IP 주소를 반환합니다.
     */
    public static String getClientIp() {
        return getRequest().map(req -> {
            String ip = req.getHeader("X-Forwarded-For");

            // [Proxy 대응] L4나 CloudFront 등을 거치지 않은 직접 요청인 경우
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                return req.getRemoteAddr();
            }

            // [Chain IP] "Client, Proxy1, Proxy2" 형식일 경우, 첫 번째가 원본 IP
            return ip.split(",")[0].trim();
        }).orElse("0.0.0.0");
    }

    public static String getUserAgent() {
        return getRequest().map(req -> req.getHeader("User-Agent")).orElse("Unknown");
    }

    /**
     * 현재 요청 URL을 기반으로 진입 채널(ADMIN, API, WEB)을 식별합니다.
     */
    public static ChannelType getChannel() {
        return getRequest().map(req -> {
            String uri = req.getRequestURI();
            if (properties == null) return ChannelType.UNKNOWN;

            var channelCfg = properties.getChannel();

            // [우선순위] 구체적인 경로(Admin API)부터 체크해야 오탐지를 방지함
            if (uri.startsWith(channelCfg.getAdminApiPrefix())) return ChannelType.ADMIN_API;
            if (uri.startsWith(channelCfg.getApiPrefix())) return ChannelType.API;
            if (uri.startsWith(channelCfg.getAdminPrefix())) return ChannelType.ADMIN;

            // 위 조건에 해당하지 않으면 일반 사용자 웹(Front)으로 간주
            return ChannelType.WEB;
        }).orElse(ChannelType.UNKNOWN);
    }

    // --- [2] Site & Tenant Context (사이트 정보) ---

    public static String getSiteCode() {
        String siteCode = TenantContext.getSiteCode();
        // 멀티 테넌트가 식별되지 않은 경우, 기본값(DEFAULT)으로 처리하여 로직 오류 방지
        return (siteCode != null && !siteCode.isBlank()) ? siteCode : "DEFAULT";
    }

    public static Optional<TenantInfo> getTenantInfo() {
        return Optional.ofNullable(TenantContext.getTenant());
    }

    // --- [3] User Context (사용자 정보) ---

    /**
     * 현재 인증된 사용자 객체(Principal)를 반환합니다.
     */
    public static Optional<CustomUserDetails> getUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // [비로그인 체크] 인증 토큰이 없거나, 익명 사용자(Anonymous)인 경우 제외
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        // Principal 타입 안전성 검증
        if (auth.getPrincipal() instanceof CustomUserDetails user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static Optional<Long> getUserId() {
        return getUser().map(CustomUserDetails::getUserId);
    }

    /**
     * [강제 검증] 비즈니스 로직상 로그인이 필수인 경우 사용합니다.
     */
    public static Long getRequiredUserId() {
        return getUserId().orElseThrow(() -> new IllegalStateException("User authentication is required."));
    }

    public static String getUsername() {
        return getUser().map(CustomUserDetails::getNickname).orElse("Guest");
    }

    public static boolean hasRole(String roleName) {
        String targetRole = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
        return getUser().map(user -> user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(targetRole::equals)
        ).orElse(false);
    }

    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public static boolean isAuthenticated() {
        return getUser().isPresent();
    }

    // --- [4] I18n & Locale (다국어 및 시간대) ---

    public static Locale getLocale() {
        // Spring LocaleResolver가 판단한 현재 요청의 언어 설정
        return LocaleContextHolder.getLocale();
    }

    public static String getLanguage() {
        return getLocale().getLanguage();
    }

    public static TimeZone getTimeZone() {
        return LocaleContextHolder.getTimeZone();
    }

    // --- [5] System Status (운영 상태) ---

    public static boolean isMaintenanceMode() {
        return TenantContext.isMaintenanceMode();
    }

    public static boolean isReadOnlyMode() {
        return TenantContext.isReadOnlyMode();
    }

    // --- [Helper Methods] ---

    /**
     * 클라이언트가 HTML이 아닌 데이터(JSON) 응답을 원하는지 판단합니다.
     */
    public static boolean isAjaxRequest() {
        return getRequest().map(req ->
                "XMLHttpRequest".equals(req.getHeader("X-Requested-With")) || // Legacy Ajax
                        "application/json".equals(req.getHeader("Accept"))            // Modern SPA/Mobile
        ).orElse(false);
    }

    private static Optional<HttpServletRequest> getRequest() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return Optional.ofNullable(attr).map(ServletRequestAttributes::getRequest);
    }
}