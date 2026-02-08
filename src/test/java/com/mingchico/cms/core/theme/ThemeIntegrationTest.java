package com.mingchico.cms.core.theme;

import com.mingchico.cms.core.menu.service.MenuResolver; // [Import]
import com.mingchico.cms.core.tenant.TenantResolver;
import com.mingchico.cms.core.tenant.domain.TenantFeatures;
import com.mingchico.cms.core.tenant.dto.TenantInfo;
import com.mingchico.cms.core.tenant.service.TenantMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import; // [Import]
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ThemeIntegrationTest.ThemeTestController.class)
class ThemeIntegrationTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ThemeResourceResolver resourceResolver;
    @MockitoBean TenantMetadataProvider tenantMetadataProvider;
    @MockitoBean TenantResolver tenantResolver;

    // [추가] 메뉴 시스템이 DB를 조회하지 않도록 모킹 (404 원인은 아니지만 노이즈 제거)
    @MockitoBean MenuResolver menuResolver;

    @BeforeEach
    void setUp() {
        // 1. 도메인 -> 사이트 코드 식별 모킹
        given(tenantResolver.resolveSiteCode(any())).willReturn("SITE_DARK");

        // 2. 사이트 코드 -> 테넌트 정보(테마) 리턴 모킹
        TenantInfo mockTenant = new TenantInfo(
                1L, "SITE_DARK", "Dark Site", "dark-mode",
                false, false, new TenantFeatures()
        );
        given(tenantMetadataProvider.getTenantInfo("SITE_DARK")).willReturn(mockTenant);

        // 3. [안전장치] 메뉴 리졸버가 아무것도 찾지 못해도 에러내지 않도록 빈 Optional 반환
        // (만약 메뉴 시스템이 엄격하다면 여기서 더미 메뉴를 리턴해야 함)
        given(menuResolver.resolve(anyString(), anyString())).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("Flow: 테넌트 식별 -> 컨트롤러 실행 -> 테마 적용 -> 뷰 이름 변경")
    void full_flow_theme_application() throws Exception {
        // Given
        // "dark-mode 테마에 해당 뷰 파일이 존재함"
        given(resourceResolver.checkThemeResourceExists("dark-mode", "page/home"))
                .willReturn(true);

        // When & Then
        mockMvc.perform(get("/test/theme/page")
                        .header("Host", "dark.mingchico.com"))
                .andDo(print())
                .andExpect(status().isOk())
                // [검증] 뷰 이름이 테마 경로로 변경되었는가?
                .andExpect(view().name("themes/dark-mode/page/home"))
                .andExpect(model().attribute("currentTheme", "dark-mode"));
    }

    @Test
    @DisplayName("Fallback: 테마 파일이 없으면 기본 뷰 경로를 유지한다")
    void full_flow_fallback() throws Exception {
        // Given
        // "파일이 없음"
        given(resourceResolver.checkThemeResourceExists("dark-mode", "page/home"))
                .willReturn(false);

        // When & Then
        mockMvc.perform(get("/test/theme/page")
                        .header("Host", "dark.mingchico.com"))
                .andDo(print())
                .andExpect(status().isOk())
                // [검증] 뷰 이름이 변경되지 않았는가?
                .andExpect(view().name("page/home"));
    }

    /**
     * [테스트용 내부 컨트롤러]
     * 상단의 @Import(ThemeTestController.class) 덕분에 이제 인식됩니다.
     */
    @RestController
    static class ThemeTestController {
        @GetMapping("/test/theme/page")
        public ModelAndView testPage() {
            return new ModelAndView("page/home");
        }
    }
}