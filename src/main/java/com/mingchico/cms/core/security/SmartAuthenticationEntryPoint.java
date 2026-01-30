package com.mingchico.cms.core.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * <h3>[스마트 인증 진입점]</h3>
 * <p>
 * 인증되지 않은 사용자(Anonymous)가 보호된 리소스에 접근할 때 동작합니다.
 * <br>
 * 브라우저 요청은 로그인 페이지로, API/AJAX 요청은 401 에러로 명확히 분기합니다.
 * Accept 헤더 검사를 추가하여 GET 방식의 API 호출도 정확히 감지합니다.
 * </p>
 */
@Component
public class SmartAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        if (isAjaxOrApiRequest(request)) {
            // [API/AJAX] 401 Unauthorized JSON 응답
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"로그인이 필요합니다.\"}");
        } else {
            // [Browser] 로그인 페이지로 리다이렉트
            response.sendRedirect("/login");
        }
    }

    private boolean isAjaxOrApiRequest(HttpServletRequest request) {
        String xRequestedWith = request.getHeader("X-Requested-With");
        String contentType = request.getHeader("Content-Type");
        String accept = request.getHeader("Accept");

        // 1. jQuery 등 전통적인 AJAX 헤더
        if ("XMLHttpRequest".equals(xRequestedWith)) {
            return true;
        }
        // 2. JSON을 본문에 담아 보내는 요청 (POST/PUT 등)
        if (contentType != null && contentType.startsWith("application/json")) {
            return true;
        }
        // 3. 응답을 JSON으로 받길 원하는 요청 (GET API 호출 등)
        if (accept != null && accept.contains("application/json")) {
            return true;
        }

        return false;
    }
}