package com.mingchico.cms.core.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * <h3>[로그인 실패 핸들러]</h3>
 * <p>
 * 로그인 실패 원인을 분석하여 적절한 에러 메시지를 생성하고 로그인 페이지로 돌려보냅니다.
 * 보안상 '아이디가 없음'과 '비밀번호 틀림'을 명확히 구분해주지 않는 것이 원칙일 수 있으나,
 * CMS 내부 운영용이라면 편의를 위해 구분할 수도 있습니다. (여기서는 보안 권장사항대로 뭉뚱그려 처리)
 * </p>
 */
@Slf4j
@Component
public class FormAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        String errorMessage = "아이디 또는 비밀번호가 올바르지 않습니다.";

        if (exception instanceof LockedException) {
            errorMessage = "계정이 잠겨있습니다. 관리자에게 문의하세요.";
        } else if (exception instanceof DisabledException) {
            errorMessage = "비활성화된 계정입니다.";
        } else if (exception instanceof BadCredentialsException) {
            // 보안을 위해 상세 내용은 숨김
            errorMessage = "아이디 또는 비밀번호를 확인해주세요.";
        }

        log.warn("[Login Failed] IP: {}, Reason: {}", request.getRemoteAddr(), exception.getMessage());

        // 한글 메시지 인코딩
        String encodedMsg = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
        setDefaultFailureUrl("/login?error=true&message=" + encodedMsg);

        super.onAuthenticationFailure(request, response, exception);
    }
}