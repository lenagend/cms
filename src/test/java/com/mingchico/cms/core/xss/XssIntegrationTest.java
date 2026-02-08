package com.mingchico.cms.core.xss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mingchico.cms.core.config.WebMvcConfig;
import com.mingchico.cms.core.mdc.MdcLoggingFilter;
import com.mingchico.cms.core.menu.interceptor.MenuAccessInterceptor;
import com.mingchico.cms.core.menu.interceptor.MenuAclInterceptor;
import com.mingchico.cms.core.menu.interceptor.MenuResolutionInterceptor;
import com.mingchico.cms.core.ratelimit.GlobalRateLimitFilter;
import com.mingchico.cms.core.security.AccessContext;
import com.mingchico.cms.core.tenant.TenantFilter;
import com.mingchico.cms.core.tenant.TenantProperties;
import com.mingchico.cms.core.theme.ThemeResourceResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TestXssController.class,
        // [중요] 필터와 자동 설정을 더 강력하게 제외하여 응답 유실 방지
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                        GlobalRateLimitFilter.class,
                        TenantFilter.class,
                        WebMvcConfig.class
                })
        },
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@Import({
        XssJacksonDeserializer.class, // JSON 정제 핵심 로직
        XssProtectionFilter.class,    // Form/Header 정제 필터
        XssProperties.class,
        TenantProperties.class,       // [추가] WebMvcConfig가 참조하는 설정 파일 (NPE 방지)
        MdcLoggingFilter.class,
        TestXssController.class,       // 테스트 대상 컨트롤러 명시
        ThemeResourceResolver.class
})
class XssIntegrationTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean
    private AccessContext accessContext;

    // --- [Fix] WebMvcConfig 초기화를 위한 Mock 빈 등록 ---
    // WebMvcConfig가 로딩될 때 필요한 의존성들을 가짜(Mock)로 채워넣어
    // 실제 Menu/User/Security 시스템을 로딩하지 않고도 테스트 컨텍스트가 뜨도록 합니다.
    @MockitoBean private MenuResolutionInterceptor resolutionInterceptor;
    @MockitoBean private MenuAccessInterceptor accessInterceptor;
    @MockitoBean private MenuAclInterceptor aclInterceptor;
    // --------------------------------------------------

    @Test
    @DisplayName("Query Parameter - GET 요청은 필터 제외로 인해 script 유지됨")
    void query_parameter_xss_test() throws Exception {
        String response = mockMvc.perform(get("/test/xss/query")
                        .param("q", "<img src=x onerror=alert(1)>hello")
                        .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("onerror");
    }

    @Test
    @DisplayName("Form Data - script 제거 확인")
    void form_data_xss_test() throws Exception {
        String response = mockMvc.perform(post("/test/xss/form")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("value", "<script>alert(1)</script>test"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("<script>").contains("test");
    }

    @Test
    @DisplayName("JSON Body - title은 정제, content는 @AllowHtml로 유지")
    void json_body_xss_test() throws Exception {
        String payload = """
            {
              "title": "<script>alert('xss')</script>hello",
              "content": "<p>본문</p><script>alert(1)</script>"
            }
            """;

        String response = mockMvc.perform(post("/test/xss/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("\"title\":\"hello\"");
        assertThat(response).contains("\"content\":\"<p>본문</p><script>alert(1)</script>\"");
    }
}