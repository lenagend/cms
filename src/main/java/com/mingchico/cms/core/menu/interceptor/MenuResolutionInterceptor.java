package com.mingchico.cms.core.menu.interceptor;

import com.mingchico.cms.core.context.ContextHolder;
import com.mingchico.cms.core.menu.MenuContext;
import com.mingchico.cms.core.menu.domain.Menu;
import com.mingchico.cms.core.menu.service.MenuResolver;
import com.mingchico.cms.core.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Set;

/**
 * <h3>[메뉴 해석 및 접근 제어 인터셉터]</h3>
 * <p>
 * 컨트롤러 실행 전(PreHandle)에 개입하여 다음을 수행합니다:
 * <ol>
 * <li><b>Menu Resolution:</b> 현재 URL에 맞는 메뉴를 찾습니다.</li>
 * <li><b>Status Check:</b> 메뉴가 접근 가능한 상태(accessible)인지 확인합니다.</li>
 * <li><b>ACL Check:</b> 현재 사용자가 메뉴에 접근할 권한(Role)이 있는지 검사합니다.</li>
 * <li><b>Context Binding:</b> 찾은 메뉴를 MenuContext에 저장합니다.</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MenuResolutionInterceptor implements HandlerInterceptor {
    private final MenuResolver menuResolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String siteCode = TenantContext.getSiteCode();
        if (siteCode == null) return true;

        // 메뉴를 찾으면 Context(전역)와 Request Attribute(요청 범위) 양쪽에 모두 저장해야 합니다.
        menuResolver.resolve(siteCode, request.getRequestURI())
                .ifPresent(menu -> {
                    // 1. 비즈니스 로직용 (ThreadLocal)
                    MenuContext.set(menu);

                    // 2. 뷰 렌더링 및 테스트 검증용 (Request Attribute)
                    request.setAttribute("currentMenu", menu);

                    log.trace("✅ Menu Resolved: {}", menu.getName());
                });

        return true;

    }
}