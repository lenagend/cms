package com.mingchico.cms.core.security;

import com.mingchico.cms.core.tenant.DomainTenantResolver;
import com.mingchico.cms.core.tenant.domain.Tenant;
import com.mingchico.cms.core.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * [SecurityConfig 통합 테스트]
 * 스프링 시큐리티의 필터 체인, 핸들러, 엔트리포인트가 유기적으로 동작하는지 검증합니다.
 * <p>
 * <b>검증 항목:</b>
 * 1. API(/api/**)와 WEB(/**) 경로에 대한 보안 정책 분리 적용 여부
 * 2. 권한(Role)에 따른 페이지 접근 제어 및 403 Forbidden 동작
 * 3. 비인증 사용자의 API 요청 시 JSON 응답(401) 처리 여부
 * 4. CSRF 활성화/비활성화 및 보안 헤더(CSP, FrameOptions 등) 적용 확인
 * </p>
 */
@SpringBootTest // 스프링 컨텍스트 전체를 로드하여 통합 테스트 수행
@AutoConfigureMockMvc // 실제 서블릿 컨테이너를 띄우지 않고 HTTP 요청을 흉내내는 MockMvc 설정
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // 실제 DB 조회 로직은 UserDetailsServiceTest에서 검증하므로,
    // 여기서는 시큐리티 설정 흐름(인가, 필터 등)만 보기 위해 유저 서비스는 가짜(Mock)로 처리합니다.
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Autowired
    private TenantRepository tenantRepository; // 테넌트 데이터를 넣기 위해 주입

    @Autowired
    private DomainTenantResolver domainTenantResolver; // 캐시 갱신을 위해 주입

    @BeforeEach
    void setUp() {
        // [중요] 테스트 도중 테넌트 필터에서 막히지 않도록 기본 테넌트 등록
        tenantRepository.deleteAll();
        tenantRepository.save(Tenant.builder()
                .domainPattern("test.mingchico.com")
                .siteCode("TEST_SITE")
                .name("테스트 사이트")
                .build());

        // Resolver의 내부 캐시 규칙을 최신화
        domainTenantResolver.refreshRules();
    }

    /**
     * [테넌트 헤더 공통 유틸리티]
     * 매번 host 헤더를 추가하는 중복을 제거하기 위한 헬퍼 메서드입니다.
     */
    private MockHttpServletRequestBuilder getWithTenant(String url) {
        return get(url).header("host", "test.mingchico.com");
    }

    private MockHttpServletRequestBuilder postWithTenant(String url) {
        return post(url).header("host", "test.mingchico.com");
    }

    @RestController
    static class TestController {
        @GetMapping("/login")
        public String login() { return "login page"; }

        @GetMapping("/favicon.ico")
        public void favicon() {}
    }

    /**
     * [공개 경로 테스트]
     * 로그인 페이지나 파비콘 등 permitAll()로 설정된 경로는 인증 없이도 접근 가능해야 합니다.
     */
    @Test
    @DisplayName("[WEB] 공개 페이지(로그인, 정적리소스)는 누구나 접근 가능하다")
    void public_pages_access() throws Exception {
        // /favicon 요청 시 200 OK 응답을 기대함
        mockMvc.perform(getWithTenant("/favicon.ico"))
                .andExpect(status().isNotFound());
    }

    /**
     * [인증 예외 테스트 - 브라우저]
     * 브라우저 기반의 일반 웹 페이지는 인증이 안 된 경우 로그인 페이지로 보내야 합니다.
     */
    @Test
    @DisplayName("[WEB] 인증되지 않은 사용자가 관리자 페이지 접근 시 로그인 페이지로 리다이렉트된다")
    void admin_page_unauthorized() throws Exception {
        // [1] 실행: 관리자 대시보드 접근 시도
        // host 헤더가 없으면 TenantFilter에서 404를 뱉고 종료되어 302 테스트가 불가능합니다.
        mockMvc.perform(getWithTenant("/admin/dashboard"))
                .andDo(print())
                // [2] 검증: 이제 테넌트 필터를 통과했으므로 시큐리티에 의해 302 리다이렉트 발생
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    /**
     * [권한 부족 테스트]
     * 로그인은 했지만 해당 페이지에 접근할 권한(Role)이 없는 경우를 테스트합니다.
     */
    @Test
    @DisplayName("[WEB] 일반 유저가 관리자 페이지 접근 시 403 Forbidden")
    @WithMockUser(username = "user@test.com", roles = "USER") // 가짜 유저(ROLE_USER) 주입
    void admin_page_forbidden() throws Exception {
        // [1] 실행: 일반 유저 권한으로 관리자 페이지 접근
        mockMvc.perform(getWithTenant("/admin/dashboard"))
                // [2] 검증: 권한 부족으로 403 Forbidden 발생 확인
                .andExpect(status().isForbidden());
    }

    /**
     * [권한 통과 테스트]
     * 적절한 권한(ADMIN)을 가진 유저가 접근할 때 필터가 정상 통과되는지 확인합니다.
     */
    @Test
    @DisplayName("[WEB] ADMIN 권한이 있으면 관리자 페이지 접근 성공")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN") // 가짜 관리자(ROLE_ADMIN) 주입
    void admin_page_success() throws Exception {
        // [1] 실행: 관리자 권한으로 접근
        mockMvc.perform(getWithTenant("/admin/dashboard"))
                .andDo(print())
                // [2] 검증: 시큐리티 필터는 통과했으나 실제 컨트롤러가 없으므로 404가 뜨는 것이 정상
                // 만약 시큐리티에서 막혔다면 401이나 403이 떴을 것임
                .andExpect(status().isNotFound());
    }

    /**
     * [API 인증 예외 테스트 - JSON]
     * API 요청(/api/**)은 인증 실패 시 리다이렉트가 아닌 JSON 에러를 반환해야 합니다.
     */
    @Test
    @DisplayName("[API] API 요청은 인증 실패 시 리다이렉트가 아니라 401 JSON을 반환해야 한다 (EntryPoint 검증)")
    void api_unauthorized_returns_json() throws Exception {
        // [1] 실행: JSON을 응답받길 원하는 API 요청 (Accept 헤더 포함)
        mockMvc.perform(getWithTenant("/api/private/data")
                        .accept(MediaType.APPLICATION_JSON))   // SmartAuthenticationEntryPoint 유도
                .andDo(print())
                // [2] 검증: SmartAuthenticationEntryPoint에 의해 401 응답 및 JSON 메시지 확인
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    /**
     * [API CSRF 비활성화 테스트]
     * REST API는 Stateless하므로 CSRF 보호를 꺼두었습니다. 토큰 없이도 요청이 가야 합니다.
     */
    @Test
    @DisplayName("[API] API 요청은 CSRF 검사를 하지 않는다")
    void api_csrf_disabled() throws Exception {
        // [1] 실행: CSRF 토큰 없이 API POST 요청 전송
        mockMvc.perform(postWithTenant("/api/data")
                        .content("{\"data\":\"test\"}")
                        .contentType(MediaType.APPLICATION_JSON))
                // [2] 검증: 403(CSRF 위반)이 아니라 401(인증 없음)이 발생했으므로 CSRF 필터는 통과됨을 의미
                .andExpect(status().isUnauthorized());
    }

    /**
     * [WEB CSRF 활성화 테스트]
     * 웹 로그인 등 브라우저 기반 요청은 반드시 CSRF 토큰이 있어야 합니다.
     */
    @Test
    @DisplayName("[WEB] WEB 요청은 CSRF 토큰이 없으면 403 에러가 발생해야 한다")
    void web_csrf_enabled() throws Exception {
        // [1] 실행: CSRF 토큰 없이 로그인 처리 시도
        mockMvc.perform(postWithTenant("/login-process")
                        .param("username", "test")
                        .param("password", "1234"))
                // [2] 검증: 보안을 위해 CSRF 토큰 부재 시 403 Forbidden 발생 확인
                .andExpect(status().isForbidden());
    }

    /**
     * [보안 헤더 테스트]
     * 서버가 응답할 때 브라우저 보안을 위한 필수 헤더들을 잘 내려주는지 확인합니다.
     */
    @Test
    @DisplayName("[보안 헤더] 응답에 필수 보안 헤더가 포함되어야 한다")
    void security_headers() throws Exception {
        // [1] 실행: 아무 페이지나 요청
        mockMvc.perform(getWithTenant("/login"))
                // [2] 검증: 클릭재킹 방지(Frame-Options), XSS 방지(CSP) 헤더 존재 확인
                .andExpect(header().exists("X-Frame-Options"))
                .andExpect(header().exists("X-Content-Type-Options"))
                .andExpect(header().exists("Content-Security-Policy"));
    }
}