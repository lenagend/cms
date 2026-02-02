package com.mingchico.cms.core.security;

import com.mingchico.cms.core.user.domain.Role;
import com.mingchico.cms.core.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;

import java.util.*;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantAwareSessionStrategyTest {

    @Mock
    private SessionRegistry sessionRegistry;
    @Mock
    private Authentication authentication;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private final String ROLE_ADMIN = Role.ADMIN.name();
    private final String ROLE_MANAGER = Role.MANAGER.name();
    private final String SITE_ENTERPRISE = "ENTERPRISE_SITE";

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    /**
     * [수정됨] SecurityProperties.SessionControl 타입과 생성자 순서를 맞춥니다.
     * Record 정의: (Map roleLimits, Map siteLimits, int defaultLimit)
     */
    private TenantAwareSessionStrategy createStrategy(Map<String, Integer> roleLimits, Map<String, Integer> siteLimits, int defaultLimit) {
        // 1. SessionControl 객체 생성 (순서 주의: roleLimits, siteLimits, defaultLimit)
        SecurityProperties.SessionControl sessionControl = new SecurityProperties.SessionControl(roleLimits, siteLimits, defaultLimit);

        // 2. SecurityProperties 생성
        SecurityProperties properties = new SecurityProperties(null, sessionControl);

        return new TenantAwareSessionStrategy(sessionRegistry, properties);
    }

    // Helper: Mock User 생성
    private CustomUserDetails createMockPrincipal(Role role, String siteCode) {
        // 테스트용 User 빌더 (실제 프로젝트 구조에 맞게 조정 필요)
        User user = User.builder().email("test@test.com").password("pw").nickname("tester").build();
        return new CustomUserDetails(user, siteCode, role, true, true);
    }

    @Test
    @DisplayName("설정파일에 ADMIN이 -1(무제한)로 설정되어 있으면, 기존 세션이 만료되지 않는다")
    void admin_unlimited_session() {
        // Given
        TenantAwareSessionStrategy strategy = createStrategy(Map.of(ROLE_ADMIN, -1), Collections.emptyMap(), 1);
        CustomUserDetails admin = createMockPrincipal(Role.ADMIN, "SITE_A");
        given(authentication.getPrincipal()).willReturn(admin);

        // 기존 세션 100개 존재 가정
        List<SessionInformation> sessions = new ArrayList<>();
        for (int i = 0; i < 100; i++) sessions.add(mock(SessionInformation.class));

        // When
        strategy.onAuthentication(authentication, request, response);

        // Then
        for (SessionInformation session : sessions) {
            verify(session, never()).expireNow();
        }
    }

    @Test
    @DisplayName("MANAGER가 5개 제한일 때, 6번째 로그인 시 가장 오래된 세션이 만료된다")
    void manager_limit_defined_in_properties() {
        // Given
        TenantAwareSessionStrategy strategy = createStrategy(Map.of(ROLE_MANAGER, 5), Collections.emptyMap(), 1);
        CustomUserDetails manager = createMockPrincipal(Role.MANAGER, "SITE_A");
        given(authentication.getPrincipal()).willReturn(manager);

        List<SessionInformation> sessions = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            SessionInformation info = mock(SessionInformation.class);
            given(info.getLastRequest()).willReturn(new Date(currentTime + (i * 1000)));
            sessions.add(info);
        }
        given(sessionRegistry.getAllSessions(manager, false)).willReturn(sessions);

        // When
        strategy.onAuthentication(authentication, request, response);

        // Then
        verify(sessions.get(0), times(1)).expireNow(); // 가장 오래된 세션 만료
        verify(sessions.get(4), never()).expireNow();  // 최신 세션 유지
    }

    @Test
    @DisplayName("특정 테넌트(ENTERPRISE) 설정이 Role 설정보다 우선하여 적용된다")
    void site_limit_overrides_role_limit() {
        // Given: Role=1, Site=10
        TenantAwareSessionStrategy strategy = createStrategy(Map.of(ROLE_MANAGER, 1), Map.of(SITE_ENTERPRISE, 10), 1);
        CustomUserDetails enterpriseUser = createMockPrincipal(Role.MANAGER, SITE_ENTERPRISE);
        given(authentication.getPrincipal()).willReturn(enterpriseUser);

        // 세션 5개 (Role 기준 초과, Site 기준 여유)
        List<SessionInformation> sessions = new ArrayList<>();
        for (int i = 0; i < 5; i++) sessions.add(mock(SessionInformation.class));
        given(sessionRegistry.getAllSessions(enterpriseUser, false)).willReturn(sessions);

        // When
        strategy.onAuthentication(authentication, request, response);

        // Then: 만료 없음
        for (SessionInformation session : sessions) {
            verify(session, never()).expireNow();
        }
    }

    @Test
    @DisplayName("설정에 없는 Role과 Site인 경우 Default 설정값을 따른다")
    void fallback_to_default_limit() {
        // Given: Default=2
        TenantAwareSessionStrategy strategy = createStrategy(Collections.emptyMap(), Collections.emptyMap(), 2);
        CustomUserDetails normalUser = createMockPrincipal(Role.USER, "NORMAL_SITE");
        given(authentication.getPrincipal()).willReturn(normalUser);

        // 세션 2개 (꽉 참 -> 다음 로그인 시 오래된 것 만료)
        List<SessionInformation> sessions = new ArrayList<>();
        SessionInformation oldSession = mock(SessionInformation.class);
        given(oldSession.getLastRequest()).willReturn(new Date(System.currentTimeMillis() - 10000));
        sessions.add(oldSession);

        SessionInformation newSession = mock(SessionInformation.class);
        given(newSession.getLastRequest()).willReturn(new Date(System.currentTimeMillis()));
        sessions.add(newSession);

        given(sessionRegistry.getAllSessions(normalUser, false)).willReturn(sessions);

        // When
        strategy.onAuthentication(authentication, request, response);

        // Then
        verify(oldSession, times(1)).expireNow();
    }
}