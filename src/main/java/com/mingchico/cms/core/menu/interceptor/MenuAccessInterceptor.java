package com.mingchico.cms.core.menu.interceptor;

import com.mingchico.cms.core.menu.MenuContext;
import com.mingchico.cms.core.menu.domain.Menu;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class MenuAccessInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Menu menu = MenuContext.getCurrentMenu().orElse(null);

        // 메뉴가 존재하는데 접근 불가(accessible=false) 상태라면 404 처리
        if (menu != null && !menu.isAccessible()) {
            response.sendError(HttpStatus.NOT_FOUND.value());
            return false;
        }
        return true;
    }
}