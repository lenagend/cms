package com.mingchico.cms.core.theme;

import com.mingchico.cms.core.tenant.TenantContext;
import com.mingchico.cms.core.tenant.domain.TenantFeatures;
import com.mingchico.cms.core.tenant.dto.TenantInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * <h3>[ThemeViewInterceptor 단위 테스트]</h3>
 * <p>
 * 컨트롤러가 반환한 ModelAndView를 가로채어,
 * 현재 테넌트의 테마 설정에 따라 뷰 경로를 변경하는지 검증합니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ThemeViewInterceptorTest {

    @Mock
    private ThemeResourceResolver resourceResolver;

    @InjectMocks
    private ThemeViewInterceptor interceptor;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void setupTenantContext(String themeName) {
        // 테스트용 테넌트 정보 주입
        TenantInfo info = new TenantInfo(
                1L, "TEST_SITE", "Test Site", themeName, 
                false, false, new TenantFeatures()
        );
        TenantContext.setContext(info);
    }

    @Test
    @DisplayName("테마 파일이 존재하면: 뷰 이름 앞에 'themes/{테마명}/'이 붙어야 한다")
    void apply_theme_when_file_exists() {
        // Given
        setupTenantContext("dark-mode");
        ModelAndView mav = new ModelAndView("board/list");

        // "dark-mode 테마에 board/list.html 파일이 있다"고 가정
        given(resourceResolver.checkThemeResourceExists("dark-mode", "board/list"))
                .willReturn(true);

        // When
        interceptor.postHandle(request, response, new Object(), mav);

        // Then
        assertThat(mav.getViewName()).isEqualTo("themes/dark-mode/board/list");
        assertThat(mav.getModel()).containsEntry("currentTheme", "dark-mode");
    }

    @Test
    @DisplayName("테마 파일이 없으면: 원래 뷰 이름을 유지해야 한다 (Fallback)")
    void fallback_to_default_when_file_missing() {
        // Given
        setupTenantContext("dark-mode");
        ModelAndView mav = new ModelAndView("board/list");

        // "파일이 없다"고 가정
        given(resourceResolver.checkThemeResourceExists("dark-mode", "board/list"))
                .willReturn(false);

        // When
        interceptor.postHandle(request, response, new Object(), mav);

        // Then
        assertThat(mav.getViewName()).isEqualTo("board/list"); // 변경 없음
        assertThat(mav.getModel()).containsEntry("currentTheme", "dark-mode");
    }

    @Test
    @DisplayName("Redirect/Forward 요청은 테마 적용 대상이 아니다")
    void skip_redirect_forward() {
        // Given
        setupTenantContext("dark-mode");
        ModelAndView mav = new ModelAndView("redirect:/home");

        // When
        interceptor.postHandle(request, response, new Object(), mav);

        // Then
        assertThat(mav.getViewName()).isEqualTo("redirect:/home"); // 건드리지 않음
    }
}