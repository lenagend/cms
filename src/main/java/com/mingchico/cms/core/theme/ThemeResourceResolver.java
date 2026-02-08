package com.mingchico.cms.core.theme;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * <h3>[테마 리소스 리졸버]</h3>
 * <p>
 * 특정 테마에 해당 뷰 파일(HTML)이 실제로 존재하는지 확인합니다.
 * <br>
 * <b>성능 최적화:</b> {@code Resource.exists()}는 디스크 I/O를 유발하므로,
 * 결과를 로컬 캐시(Caffeine)에 저장하여 성능 저하를 방지합니다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThemeResourceResolver {

    private final ResourceLoader resourceLoader;

    // [Cache] Key: "themeName:viewName", Value: 존재 여부(Boolean)
    private final Cache<String, Boolean> resourceExistenceCache = Caffeine.newBuilder()
            .maximumSize(5000) // 뷰 템플릿 개수는 유한하므로 적절히 설정
            .expireAfterWrite(Duration.ofMinutes(30)) // 개발 모드에서는 짧게, 운영에선 길게 조정
            .build();

    /**
     * 해당 테마 경로에 뷰 파일이 존재하는지 확인합니다.
     *
     * @param themeName 테넌트의 테마 이름 (예: "dark-mode")
     * @param viewName  컨트롤러가 반환한 뷰 이름 (예: "board/list")
     * @return 존재하면 true, 아니면 false
     */
    public boolean checkThemeResourceExists(String themeName, String viewName) {
        String cacheKey = themeName + ":" + viewName;

        return resourceExistenceCache.get(cacheKey, key -> {
            // Thymeleaf 기본 경로 규칙: classpath:/templates/ + path + .html
            String path = "classpath:/templates/themes/" + themeName + "/" + viewName + ".html";
            Resource resource = resourceLoader.getResource(path);
            boolean exists = resource.exists();
            
            if (!exists) {
                log.trace("Theme resource not found: {}", path);
            }
            return exists;
        });
    }
}