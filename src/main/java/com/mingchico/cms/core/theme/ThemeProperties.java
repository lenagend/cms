package com.mingchico.cms.core.theme;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cms.theme")
public class ThemeProperties {

    /** 지원하는 테마 목록 (Catalog) */
    private List<ThemeDefinition> availableThemes = new ArrayList<>();

    /**
     * 입력받은 테마 코드가 유효한지 검증합니다.
     * @param code 검사할 테마 코드 (예: "default", "minimal")
     * @return 유효하면 true
     */
    public boolean isValidTheme(String code) {
        if (code == null || code.isBlank()) return false;
        return availableThemes.stream()
                .anyMatch(theme -> theme.code().equals(code));
    }

    public record ThemeDefinition(
            String code,
            String displayName,
            String description
    ) {}
}