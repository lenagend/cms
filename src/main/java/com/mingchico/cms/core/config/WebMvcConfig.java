package com.mingchico.cms.core.config;

import com.mingchico.cms.core.menu.interceptor.MenuAccessInterceptor;
import com.mingchico.cms.core.menu.interceptor.MenuAclInterceptor;
import com.mingchico.cms.core.menu.interceptor.MenuResolutionInterceptor;
import com.mingchico.cms.core.tenant.TenantProperties;
import com.mingchico.cms.core.theme.ThemeViewInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * <h3>[웹 MVC 설정]</h3>
 * <p>
 * 인터셉터 등록 및 실행 순서를 제어합니다.
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final MenuResolutionInterceptor resolutionInterceptor;
    private final MenuAccessInterceptor accessInterceptor;
    private final MenuAclInterceptor aclInterceptor;
    private final TenantProperties tenantProperties;
    private final ThemeViewInterceptor themeViewInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 순서(order)를 명시하여 파이프라인 흐름을 강제합니다.
        registry.addInterceptor(resolutionInterceptor).addPathPatterns("/**").order(1);
        registry.addInterceptor(accessInterceptor).addPathPatterns("/**").order(2);
        registry.addInterceptor(aclInterceptor).addPathPatterns("/**").order(3)
                .excludePathPatterns(tenantProperties.getExcludedPaths());

        registry.addInterceptor(themeViewInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(tenantProperties.getExcludedPaths()) // 정적 리소스 등 제외
                .order(100); // 가장 늦게(뷰 렌더링 직전) 실행
    }
}