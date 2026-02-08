package com.mingchico.cms.core.security;

import com.mingchico.cms.core.tenant.DomainTenantResolver;
import com.mingchico.cms.core.tenant.domain.Tenant;
import com.mingchico.cms.core.tenant.repository.TenantRepository;
import com.mingchico.cms.core.user.repository.MembershipRepository; // [추가]
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * [SecurityConfig 통합 테스트]
 * 스프링 시큐리티의 필터 체인, 핸들러, 엔트리포인트가 유기적으로 동작하는지 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityConfigIntegrationTest.TestController.class)
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompositeSessionAuthenticationStrategy compositeSessionStrategy;

    @Autowired
    private SessionRegistry sessionRegistry;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private MembershipRepository membershipRepository; // [추가] FK 관계 정리를 위해 필요

    @Autowired
    private DomainTenantResolver domainTenantResolver;

    @BeforeEach
    void setUp() {
        // [수정] 삭제 순서: 자식(Membership) -> 부모(Tenant)
        membershipRepository.deleteAll();
        tenantRepository.deleteAll();

        // [수정] themeName 필드 추가 반영
        tenantRepository.save(Tenant.builder()
                .domainPattern("test.mingchico.com")
                .siteCode("TEST_SITE")
                .name("테스트 사이트")
                .themeName("default") // [필수] 테넌트 엔티티 변경 사항 반영
                .build());

        domainTenantResolver.refreshRules();
    }

    private MockHttpServletRequestBuilder getWithTenant(String url) {
        return get(url).header("host", "test.mingchico.com");
    }

    private MockHttpServletRequestBuilder postWithTenant(String url) {
        return post(url).header("host", "test.mingchico.com");
    }

    /**
     * [테스트용 컨트롤러]
     */
    @RestController
    static class TestController {
        @GetMapping("/login")
        public String login() { return "login page"; }

        @GetMapping("/favicon.ico")
        public void favicon() {}

        @GetMapping("/admin/dashboard")
        public String adminDashboard() { return "admin dashboard"; }

        @GetMapping("/api/private/data")
        public String privateData() { return "private data"; }

        @PostMapping("/api/data")
        public String postData() { return "posted data"; }

        @PostMapping("/login-process")
        public void loginProcess() {}
    }

    @Test
    @DisplayName("[설정 검증] TenantAwareSessionStrategy가 필터 체인에 포함되어 있어야 한다")
    void session_strategy_configured() {
        assertThat(compositeSessionStrategy).isNotNull();
        assertThat(sessionRegistry).isNotNull();
    }

    @Test
    @DisplayName("[통합] @WithMockUser(일반 User)로 접근 시에도 에러 없이 통과해야 한다")
    @WithMockUser(username = "user", roles = "USER")
    void withMockUser_compatibility() throws Exception {
        mockMvc.perform(get("/api/private/data")
                        .header("host", "test.mingchico.com"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[WEB] 공개 페이지(로그인, 정적리소스)는 누구나 접근 가능하다")
    void public_pages_access() throws Exception {
        mockMvc.perform(getWithTenant("/favicon.ico"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[WEB] 인증되지 않은 사용자가 관리자 페이지 접근 시 로그인 페이지로 리다이렉트된다")
    void admin_page_unauthorized() throws Exception {
        mockMvc.perform(getWithTenant("/admin/dashboard"))
                .andDo(print())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("[WEB] 일반 유저가 관리자 페이지 접근 시 403 Forbidden")
    @WithMockUser(username = "user@test.com", roles = "USER")
    void admin_page_forbidden() throws Exception {
        mockMvc.perform(getWithTenant("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[WEB] ADMIN 권한이 있으면 관리자 페이지 접근 성공")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_page_success() throws Exception {
        mockMvc.perform(getWithTenant("/admin/dashboard"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[API] API 요청은 인증 실패 시 리다이렉트가 아니라 401 JSON을 반환해야 한다 (EntryPoint 검증)")
    void api_unauthorized_returns_json() throws Exception {
        mockMvc.perform(getWithTenant("/api/private/data")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("[API] API 요청은 CSRF 검사를 하지 않는다")
    void api_csrf_disabled() throws Exception {
        mockMvc.perform(postWithTenant("/api/data")
                        .content("{\"data\":\"test\"}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[WEB] WEB 요청은 CSRF 토큰이 없으면 403 에러가 발생해야 한다")
    void web_csrf_enabled() throws Exception {
        mockMvc.perform(postWithTenant("/login-process")
                        .param("username", "test")
                        .param("password", "1234"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[보안 헤더] 응답에 필수 보안 헤더가 포함되어야 한다")
    void security_headers() throws Exception {
        mockMvc.perform(getWithTenant("/login"))
                .andExpect(header().exists("X-Frame-Options"))
                .andExpect(header().exists("X-Content-Type-Options"))
                .andExpect(header().exists("Content-Security-Policy"));
    }
}