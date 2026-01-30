package com.mingchico.cms.core.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * <h3>[로그인 성공 핸들러]</h3>
 * <p>
 * 로그인 성공 직후 실행되며, 다음 기능을 수행합니다.
 * 1. 감사 로그(Audit Log) 기록
 * 2. 리다이렉트 URL 검증 (Open Redirect 취약점 방어)
 * 3. 사용자 편의를 위해 요청했던 페이지로 이동
 * </p>
 */
@Slf4j
@Component
public class FormAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        String username = authentication.getName();
        String clientIp = request.getRemoteAddr(); // 실제 운영 시엔 GlobalRateLimitFilter 등에서 파싱한 실제 IP 사용 권장

        // [Audit] 로그인 성공 로그
        // TODO: 프로덕션에서는 성공로드 DB에 저장
        log.info("[Login Success] User: {}, IP: {}", username, clientIp);

        // SavedRequest: 사용자가 로그인 페이지로 튕기기 전에 요청했던 URL 정보
        SavedRequest savedRequest = requestCache.getRequest(request, response);

        if (savedRequest != null) {
            String targetUrl = savedRequest.getRedirectUrl();

            // [보안 개선] Open Redirect 방지: 타겟 URL이 우리 도메인 내부인지 검증
            if (isSafeRedirectUrl(request, targetUrl)) {
                getRedirectStrategy().sendRedirect(request, response, targetUrl);
            } else {
                log.warn("[Security Alert] Open Redirect Attempt detected! User: {}, Target: {}", username, targetUrl);
                // 의심스러운 리다이렉트 시도는 무시하고 홈으로 보냄
                getRedirectStrategy().sendRedirect(request, response, "/");
            }
        } else {
            // 별도 요청 없이 로그인 버튼을 눌러 들어온 경우
            setDefaultTargetUrl("/");
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }

    /**
     * 리다이렉트 대상 URL이 안전한지(같은 호스트인지) 검증합니다.
     */
    private boolean isSafeRedirectUrl(HttpServletRequest request, String targetUrl) {
        if (!StringUtils.hasText(targetUrl)) {
            return false;
        }

        // 절대 경로(/foo)는 안전
        if (targetUrl.startsWith("/")) {
            return true;
        }

        // 전체 URL(http://...)인 경우 호스트 검증 필요
        UriComponents uriComponents = UriComponentsBuilder.fromUriString(targetUrl).build();
        String targetHost = uriComponents.getHost();
        String currentHost = request.getServerName();

        // 호스트가 일치하거나 null(상대경로)인 경우만 허용
        return targetHost == null || targetHost.equalsIgnoreCase(currentHost);
    }
}