package com.mingchico.cms.core.theme;

import com.mingchico.cms.core.tenant.TenantContext;
import com.mingchico.cms.core.tenant.dto.TenantInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * <h3>[테마 뷰 인터셉터]</h3>
 * <p>
 * 컨트롤러가 처리를 마친 후(PostHandle), 뷰 렌더링 직전에 개입하여
 * 현재 테넌트의 테마 설정에 맞춰 <b>View Name</b>을 동적으로 변경합니다.
 * </p>
 *
 * <h3>[Fallback 전략]</h3>
 * <ol>
 * <li>테마 폴더 확인: {@code themes/{themeName}/{viewName}}</li>
 * <li>파일이 존재하면 -> 뷰 이름 변경</li>
 * <li>파일이 없으면 -> 원래 뷰 이름 유지 (기본 템플릿 사용)</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThemeViewInterceptor implements HandlerInterceptor {

    private final ThemeResourceResolver resourceResolver;

    private static final String THEME_PREFIX = "themes/";

    @Override
    public void postHandle(@NonNull HttpServletRequest request,
                           @NonNull HttpServletResponse response,
                           @NonNull Object handler,
                           ModelAndView modelAndView) {

        // 1. 유효성 검사 (ModelAndView가 없거나, 리다이렉트인 경우 제외)
        if (modelAndView == null || !modelAndView.hasView()) {
            return;
        }
        String originalView = modelAndView.getViewName();
        if (originalView == null || originalView.startsWith("redirect:") || originalView.startsWith("forward:")) {
            return;
        }

        // 2. 테넌트 정보 및 테마 확인
        TenantInfo tenant = TenantContext.getTenant();
        if (tenant == null) {
            return; // 테넌트 정보가 없으면 기본 뷰 사용
        }

        String themeName = tenant.themeName();
        // "default" 테마는 굳이 themes 폴더를 거치지 않고 기본 경로(templates/)를 쓸 수도 있음.
        // 여기서는 명시적으로 default 폴더를 쓴다고 가정하거나, 정책에 따라 분기 가능.
        if ("default".equals(themeName)) {
            // [정책 선택] default 테마도 themes/default/ 밑에 둘 것인가?
            // 아니면 바로 templates/ 밑을 볼 것인가?
            // 여기서는 'Fallback' 로직이 있으므로 일단 패스하고 없으면 기본을 보도록 함.
        }

        // 3. 테마 전용 뷰 존재 여부 확인 (캐싱됨)
        if (resourceResolver.checkThemeResourceExists(themeName, originalView)) {
            String newViewName = THEME_PREFIX + themeName + "/" + originalView;
            modelAndView.setViewName(newViewName);
            log.trace("🎨 Theme Applied: {} -> {}", originalView, newViewName);
        } else {
            // 4. Fallback: 테마 파일이 없으면 원래 경로(templates/...) 사용
            log.trace("⚠️ Theme resource missing, falling back to default: {}", originalView);
        }
        
        // [Tip] 뷰(HTML)에서 현재 테마 이름을 쓸 수 있도록 모델에 추가
        modelAndView.addObject("currentTheme", themeName);
    }
}