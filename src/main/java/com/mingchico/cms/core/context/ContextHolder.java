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
 * <h3>주요 특징</h3>
 * <ul>
 * <li><b>Null-Safe:</b> 데이터 부재 시 {@link Optional} 또는 합리적인 기본값(Default)을 반환합니다.</li>
 * <li><b>Unified Access:</b> SecurityContext, MDC, RequestAttributes 등 복잡한 내부 API를 감춥니다.</li>
 * <li><b>Maintenance Ready:</b> 유지보수 모드, 읽기 전용 모드 등 운영 플래그 확장이 고려되었습니다.</li>
 * </ul>
 */
@UtilityClass
public class ContextHolder {

    @Setter
    private static ContextProperties properties;

    // --- [1] Request & Trace Context (요청 추적 및 클라이언트 정보) ---

    /**
     * 현재 요청의 추적 ID (Trace ID)를 반환합니다.
     * @return MDC에 저장된 correlationId (없으면 "UNKNOWN")
     * @see com.mingchico.cms.core.mdc.MdcLoggingFilter
     */
    public static String getRequestId() {
        return Optional.ofNullable(MDC.get(MdcLoggingFilter.MDC_KEY)).orElse("UNKNOWN");
    }

    /**
     * 클라이언트의 실제 IP 주소를 반환합니다.
     * <p>X-Forwarded-For 헤더를 우선 확인하여 프록시/L4 뒤의 원본 IP를 추적합니다.</p>
     */
    public static String getClientIp() {
        return getRequest().map(req -> {
            String ip = req.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                return req.getRemoteAddr();
            }
            // "client, proxy1, proxy2" 형식일 경우 첫 번째 IP 반환
            return ip.split(",")[0].trim();
        }).orElse("0.0.0.0");
    }

    /**
     * 클라이언트의 User-Agent 정보를 반환합니다.
     */
    public static String getUserAgent() {
        return getRequest().map(req -> req.getHeader("User-Agent")).orElse("Unknown");
    }

    /**
     * 현재 요청 채널 식별 (yml 설정 기반)
     */
    public static ChannelType getChannel() {
        return getRequest().map(req -> {
            String uri = req.getRequestURI();
            if (properties == null) return ChannelType.UNKNOWN;

            var channelCfg = properties.getChannel();
            if (uri.startsWith(channelCfg.getAdminApiPrefix())) return ChannelType.ADMIN_API;
            if (uri.startsWith(channelCfg.getApiPrefix())) return ChannelType.API;
            if (uri.startsWith(channelCfg.getAdminPrefix())) return ChannelType.ADMIN;
            return ChannelType.WEB;
        }).orElse(ChannelType.UNKNOWN);
    }

    // --- [2] Site & Tenant Context (사이트 정보) ---

    /**
     * 현재 접속 중인 사이트 코드(Tenant ID)를 반환합니다.
     * @return 식별된 사이트 코드 (기본값: "DEFAULT")
     * @see com.mingchico.cms.core.tenant.TenantContext
     */
    public static String getSiteCode() {
        String siteCode = TenantContext.getSiteCode();
        return (siteCode != null && !siteCode.isBlank()) ? siteCode : "DEFAULT";
    }

    /**
     * [New] 현재 테넌트의 상세 메타데이터(이름, 읽기전용 여부 등)를 반환합니다.
     * <p>필요 시 이름이나 도메인 패턴 등을 꺼내 쓸 수 있습니다.</p>
     * @return Optional TenantInfo
     */
    public static Optional<TenantInfo> getTenantInfo() {
        return Optional.ofNullable(TenantContext.getTenant());
    }

    // --- [3] User Context (사용자 정보) ---

    /**
     * 현재 인증된 사용자 객체(Principal)를 반환합니다.
     * @return 로그인 상태면 Optional<CustomUserDetails>, 아니면 Empty
     */
    public static Optional<CustomUserDetails> getUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        if (auth.getPrincipal() instanceof CustomUserDetails user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /**
     * 현재 사용자 ID (PK)를 반환합니다.
     */
    public static Optional<Long> getUserId() {
        return getUser().map(CustomUserDetails::getUserId);
    }

    /**
     * [필수] 현재 사용자 ID를 반환하며, 비로그인 상태일 경우 예외를 던집니다.
     * @throws IllegalStateException 로그인하지 않은 사용자가 접근했을 때
     */
    public static Long getRequiredUserId() {
        return getUserId().orElseThrow(() -> new IllegalStateException("User authentication is required."));
    }

    /**
     * 현재 사용자 이름(닉네임 또는 이름)을 반환합니다.
     */
    public static String getUsername() {
        return getUser().map(CustomUserDetails::getNickname).orElse("Guest");
    }

    /**
     * 현재 사용자가 특정 역할을 가지고 있는지 확인합니다.
     * @param roleName 역할 명 (예: "ADMIN") -> 내부적으로 "ROLE_ADMIN" 체크
     */
    public static boolean hasRole(String roleName) {
        String targetRole = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
        return getUser().map(user -> user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(targetRole::equals)
        ).orElse(false);
    }

    /**
     * 현재 사용자가 시스템 전체 관리자(SUPER ADMIN)인지 확인합니다.
     */
    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public static boolean isAuthenticated() {
        return getUser().isPresent();
    }

    // --- [4] I18n & Locale (다국어 및 시간대) ---

    /**
     * 현재 요청의 로케일(언어)을 반환합니다.
     * @see LocaleContextHolder
     */
    public static Locale getLocale() {
        return LocaleContextHolder.getLocale();
    }

    /**
     * 현재 언어 코드를 반환합니다. (예: "ko", "en")
     */
    public static String getLanguage() {
        return getLocale().getLanguage();
    }

    /**
     * 현재 클라이언트의 시간대를 반환합니다.
     */
    public static TimeZone getTimeZone() {
        return LocaleContextHolder.getTimeZone();
    }

    // --- [5] System Status (운영 상태) ---

    /**
     * 사이트 유지보수(점검) 모드 여부
     */
    public static boolean isMaintenanceMode() {
        return TenantContext.isMaintenanceMode();
    }

    /**
     * 사이트 읽기 전용 모드 여부 (점검 중이거나 정책상 제한)
     * <p>TenantContext에 판단 로직을 위임하여 일관성을 보장합니다.</p>
     */
    public static boolean isReadOnlyMode() {
        return TenantContext.isReadOnlyMode();
    }

    // --- [Helper Methods] ---

    /**
     * 현재 요청이 AJAX(비동기) 요청인지 확인합니다.
     * (에러 발생 시 HTML 대신 JSON 응답을 줄 때 유용)
     */
    public static boolean isAjaxRequest() {
        return getRequest().map(req ->
                "XMLHttpRequest".equals(req.getHeader("X-Requested-With")) ||
                        "application/json".equals(req.getHeader("Accept")) // 대안적 체크
        ).orElse(false);
    }

    private static Optional<HttpServletRequest> getRequest() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return Optional.ofNullable(attr).map(ServletRequestAttributes::getRequest);
    }
}