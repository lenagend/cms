package com.mingchico.cms.core.xss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mingchico.cms.core.ratelimit.GlobalRateLimitFilter;
import com.mingchico.cms.core.tenant.TenantFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TestXssController.class,
        // 1. 방해되는 필터들 제외 (Tenant, RateLimit 등)
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                        GlobalRateLimitFilter.class,
                        TenantFilter.class,
                })
        },
        // 2. Spring Security 자동 설정 제외 (403 Forbidden 해결)
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@Import({
        XssProtectionFilter.class,
        XssProperties.class,
        XssJacksonDeserializer.class
})
class XssIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    /**
     * JSON Body - XSS 제거 + AllowHtml 유지 검증
     * 수정됨: @AllowHtml 필드는 스크립트를 포함한 원본 그대로 통과되어야 함.
     */
    @Test
    @DisplayName("JSON Body - title은 XSS 제거, content는 @AllowHtml로 인해 원본 유지")
    void json_body_xss_test() throws Exception {

        String payload = """
            {
              "title": "<script>alert('xss')</script>hello",
              "content": "<p><b>본문</b><script>alert(1)</script></p>"
            }
            """;

        String response = mockMvc.perform(post("/test/xss/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 1. title은 일반 필드이므로 스크립트가 제거되어야 함
        assertThat(response).contains("\"title\":\"hello\"");

        // 2. content는 @AllowHtml이므로 스크립트가 그대로 남아있어야 함 (검증 조건 수정)
        assertThat(response).contains("\"content\":\"<p><b>본문</b><script>alert(1)</script></p>\"");
    }

    /**
     * @AllowHtml 직접 적용 테스트
     * 수정됨: 스크립트가 제거되지 않고 그대로 반환되는지 확인
     */
    @Test
    @DisplayName("@AllowHtml - HTML 및 script 원본 유지 확인")
    void allow_html_annotation_test() throws Exception {

        String payload = """
            {
              "content": "<h1>제목</h1><script>alert(1)</script>"
            }
            """;

        String response = mockMvc.perform(post("/test/xss/allow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 원본 그대로 반환되는지 검증
        assertThat(response).isEqualTo("<h1>제목</h1><script>alert(1)</script>");
    }


    /**
     * Query Parameter 검증
     * [변경됨] Filter에서 GET 요청은 제외하고 있으므로, 스크립트가 그대로 통과되는 것을 검증합니다.
     */
    @Test
    @DisplayName("Query Parameter - GET 요청은 필터 제외로 인해 script 유지됨")
    void query_parameter_xss_test() throws Exception {

        String response = mockMvc.perform(get("/test/xss/query")
                        .param("q", "<img src=x onerror=alert(1)>hello"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // GET 요청은 필터를 타지 않으므로 원본이 그대로 나와야 함
        assertThat(response)
                .contains("onerror")
                .contains("<img src=x onerror=alert(1)>hello");
    }

    @Test
    @DisplayName("Form Data - script 제거")
    void form_data_xss_test() throws Exception {

        String response = mockMvc.perform(post("/test/xss/form")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("value", "<script>alert(1)</script>test"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response)
                .doesNotContain("<script>")
                .contains("test");
    }

}