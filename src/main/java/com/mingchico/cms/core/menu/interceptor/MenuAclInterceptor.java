package com.mingchico.cms.core.menu.interceptor;

import com.mingchico.cms.core.menu.MenuContext;
import com.mingchico.cms.core.menu.domain.Menu;
import com.mingchico.cms.core.security.AccessContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class MenuAclInterceptor implements HandlerInterceptor {

    // [피드백 반영] 직접적인 ContextHolder 의존 대신 인터페이스 주입
    private final AccessContext accessContext;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Menu menu = MenuContext.getCurrentMenu().orElse(null);
        if (menu == null) return true;

        Set<String> requiredRoles = menu.getReadRoleSet();

        // 1. 비회원 허용 메뉴라면 통과
        if (requiredRoles.contains("ANONYMOUS")) return true;

        // 2. 권한 체크 (AccessContext 활용)
        if (!accessContext.isAuthenticated() ||
                requiredRoles.stream().noneMatch(accessContext::hasRole)) {
            throw new AccessDeniedException("해당 메뉴에 접근할 권한이 없습니다.");
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // [피드백 반영] ThreadLocal 자원 해제 보장
        MenuContext.clear();
    }
}