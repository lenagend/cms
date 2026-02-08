package com.mingchico.cms.core.theme;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * <h3>[ThemeResourceResolver 단위 테스트]</h3>
 * <p>
 * 1. 실제 파일 존재 여부를 ResourceLoader를 통해 확인하는지 검증합니다.
 * 2. <b>Caffeine Cache</b>가 적용되어, 동일한 요청 시 디스크 I/O(ResourceLoader 호출)가
 * 반복되지 않는지 확인합니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ThemeResourceResolverTest {

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource resource;

    @InjectMocks
    private ThemeResourceResolver themeResourceResolver;

    @Test
    @DisplayName("파일이 실제로 존재하면 true를 반환하고 캐싱한다")
    void resolve_resource_exists() {
        // Given
        String themeName = "dark-mode";
        String viewName = "board/list";
        String expectedPath = "classpath:/templates/themes/dark-mode/board/list.html";

        // ResourceLoader가 "파일이 존재함"을 리턴하도록 설정
        given(resourceLoader.getResource(expectedPath)).willReturn(resource);
        given(resource.exists()).willReturn(true);

        // When
        // 1. 첫 번째 호출 (Cache Miss -> DB/Disk 조회)
        boolean firstResult = themeResourceResolver.checkThemeResourceExists(themeName, viewName);
        
        // 2. 두 번째 호출 (Cache Hit -> 메모리 조회)
        boolean secondResult = themeResourceResolver.checkThemeResourceExists(themeName, viewName);

        // Then
        assertThat(firstResult).isTrue();
        assertThat(secondResult).isTrue();

        // [핵심 검증] ResourceLoader는 단 한 번만 호출되어야 함 (캐시 작동 확인)
        verify(resourceLoader, times(1)).getResource(anyString());
    }

    @Test
    @DisplayName("파일이 없으면 false를 반환하고, 이 결과 또한 캐싱한다")
    void resolve_resource_not_found() {
        // Given
        String themeName = "unknown-theme";
        String viewName = "invalid/view";

        given(resourceLoader.getResource(anyString())).willReturn(resource);
        given(resource.exists()).willReturn(false);

        // When
        boolean result = themeResourceResolver.checkThemeResourceExists(themeName, viewName);
        boolean cachedResult = themeResourceResolver.checkThemeResourceExists(themeName, viewName);

        // Then
        assertThat(result).isFalse();
        assertThat(cachedResult).isFalse();
        
        // [핵심 검증] '없다'는 사실도 캐싱되어야 불필요한 탐색을 막음
        verify(resourceLoader, times(1)).getResource(anyString());
    }
}