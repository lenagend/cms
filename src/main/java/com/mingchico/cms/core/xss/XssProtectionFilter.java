package com.mingchico.cms.core.xss;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * <h3>[XSS 방어 필터]</h3>
 *
 * <p>
 * HTTP 요청을 {@link XssRequestWrapper}로 감싸는 역할만 수행하는 필터입니다.
 * XSS 정책 판단(경로, 파라미터, 허용 여부)은 Wrapper 및 Rule 레이어에서 처리됩니다.
 * </p>
 *
 * <p>
 * 이 필터는 <strong>정책 판단을 하지 않으며</strong>,
 * 오직 정제가 필요한 요청에 Wrapper를 적용하는 책임만 가집니다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(
        name = "cms.security.xss.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class XssProtectionFilter extends OncePerRequestFilter {

    private final XssProperties properties;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String contentType = request.getContentType();

        /*
         * GET 요청은 서버 상태를 변경하지 않으며,
         * 대부분 Query Parameter는 출력 시점에서 escape 처리되므로
         * XSS RequestWrapper 적용 대상에서 제외합니다.
         */
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        /*
         * application/json 요청은 Jackson Deserializer에서
         * XSS 정제를 수행하므로 Filter 단계에서는 중복 처리하지 않습니다.
         */
        if (contentType != null && contentType.contains("application/json")) {
            filterChain.doFilter(request, response);
            return;
        }

        /*
         * 그 외 모든 요청(Form, multipart 등)에 대해
         * XssRequestWrapper를 적용합니다.
         */
        XssRequestWrapper wrappedRequest =
                new XssRequestWrapper(request, properties, request.getRequestURI());

        filterChain.doFilter(wrappedRequest, response);
    }
}
