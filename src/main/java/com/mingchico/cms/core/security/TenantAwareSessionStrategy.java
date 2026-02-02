package com.mingchico.cms.core.security;

import com.mingchico.cms.core.user.domain.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;

/**
 * <h3>[테넌트 인식형 동시 접속 제어 전략]</h3>
 * <p>
 * Spring Security의 {@link ConcurrentSessionControlAuthenticationStrategy}를 확장하여,
 * <b>{@link SecurityProperties} 설정</b>에 따라 동적으로 최대 세션 허용 수를 결정합니다.
 * </p>
 */
@Slf4j
public class TenantAwareSessionStrategy extends ConcurrentSessionControlAuthenticationStrategy {

    private static final int UNLIMITED = -1;

    private final SecurityProperties securityProperties;

    public TenantAwareSessionStrategy(SessionRegistry sessionRegistry, SecurityProperties securityProperties) {
        super(sessionRegistry);
        this.securityProperties = securityProperties;
        // true: 허용 개수 초과 시 신규 로그인 차단 (Exception)
        // false: 기존 세션 만료 (Kick out)
        super.setExceptionIfMaximumExceeded(false);
    }

    /**
     * [정책 결정 메서드] Override 수정
     * 부모 클래스의 메서드명은 'getMaximumSessionsForThisUser' 입니다.
     */
    @Override
    protected int getMaximumSessionsForThisUser(Authentication authentication) {
        if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            String roleName = userDetails.getRole().name();
            String siteCode = userDetails.getSiteCode();
            var sessionConfig = securityProperties.session();

            if (sessionConfig == null) {
                return 1;
            }

            // 1. [우선순위 1] 특정 테넌트(Site)별 특화 정책
            if (sessionConfig.siteLimits() != null && sessionConfig.siteLimits().containsKey(siteCode)) {
                int limit = sessionConfig.siteLimits().get(siteCode);
                log.debug("User [{}] applied SITE policy [{}]: limit={}", userDetails.getUsername(), siteCode, limit);
                return limit;
            }

            // 2. [우선순위 2] 사용자 권한(Role)별 정책
            if (sessionConfig.roleLimits() != null && sessionConfig.roleLimits().containsKey(roleName)) {
                int limit = sessionConfig.roleLimits().get(roleName);
                log.debug("User [{}] applied ROLE policy [{}]: limit={}", userDetails.getUsername(), roleName, limit);
                return limit;
            }

            // 3. [우선순위 3] 설정된 기본값
            return sessionConfig.defaultLimit();
        }

        // 비정상적인 인증 객체인 경우 안전하게 1 반환
        return 1;
    }
}