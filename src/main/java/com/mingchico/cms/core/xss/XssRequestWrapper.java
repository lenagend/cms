package com.mingchico.cms.core.xss;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.util.AntPathMatcher;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * <h3>[XSS 방어 래퍼 (Decorator)]</h3>
 * <p>
 * {@link HttpServletRequest}를 감싸서(Wrapping), 클라이언트가 보낸 파라미터 값을
 * 애플리케이션이 읽으려 할 때 자동으로 스크립트 태그를 제거(Sanitize)하여 반환합니다.
 * </p>
 * * <h3>동작 원리</h3>
 * <ul>
 * <li><b>Jsoup 활용:</b> 단순 문자열 치환이 아닌, HTML 파서를 사용하여 강력하게 태그를 발라냅니다.</li>
 * <li><b>Safelist.none():</b> 모든 HTML 태그를 제거하고 텍스트만 남깁니다. (일반적인 입력폼 보안)</li>
 * </ul>
 */
@Slf4j
public class XssRequestWrapper extends HttpServletRequestWrapper {
    private final XssProperties properties;
    private final String currentPath;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public XssRequestWrapper(HttpServletRequest request, XssProperties properties, String currentPath) {
        super(request);
        this.properties = properties;
        this.currentPath = currentPath;
    }

    private String cleanXss(String name, String value) {
        if (value == null) return null;

        // 현재 경로에 매칭되는 룰 찾기 (캐싱 최적화 가능)
        XssProperties.XssRule matchedRule = properties.getRules().stream()
                .filter(rule -> pathMatcher.match(rule.getPathPattern(), currentPath))
                .findFirst()
                .orElse(null);

        // 1. 룰이 없거나, 룰에 허용 파라미터 설정이 없으면 전체 정제
        if (matchedRule == null || !matchedRule.getAllowParameters().contains(name)) {
            return Jsoup.clean(value, Safelist.none());
        }

        // 2. 허용 리스트에 있으면 HTML 유지 (필요 시 Safelist.relaxed() 적용 추천)
        return value;
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return cleanXss(name, value);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        
        String[] encodedValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            encodedValues[i] = cleanXss(name, values[i]);
        }
        return encodedValues;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> map = super.getParameterMap();
        if (map == null) return null;

        Map<String, String[]> encodedMap = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : map.entrySet()) {
            String name = entry.getKey();
            String[] values = entry.getValue();
            if (values != null) {
                String[] encodedValues = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    encodedValues[i] = cleanXss(name, values[i]);
                }
                encodedMap.put(name, encodedValues);
            }
        }
        return Collections.unmodifiableMap(encodedMap);
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return cleanXss(name, value);
    }

}