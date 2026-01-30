package com.mingchico.cms.core.xss;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * <h3>[XSS 방어 설정 프로퍼티]</h3>
 * <p>
 * XSS(Cross-Site Scripting) 방어 필터의 동작 방식을 제어합니다.
 * CMS 특성상 HTML 에디터를 사용하는 구간은 필터링에서 제외해야 하므로,
 * 정교한 예외 처리가 가능하도록 설계되었습니다.
 * </p>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cms.security.xss")
public class XssProperties {

    /**
     * XSS 필터 활성화 여부
     */
    private boolean enabled = true;

    private List<XssRule> rules = new ArrayList<>();

    @Getter @Setter
    public static class XssRule {
        private String pathPattern;
        private boolean ignore = false; // 경로 전체 제외 여부
        private Set<String> allowParameters = Collections.emptySet(); // HTML 허용 파라미터들
    }

}