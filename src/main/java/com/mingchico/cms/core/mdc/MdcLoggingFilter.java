package com.mingchico.cms.core.mdc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * <h2>MDC 로깅 필터 (Request Trace Filter)</h2>
 * <p>
 * 시스템으로 들어오는 모든 HTTP 요청에 대해 꼬리표(Correlation ID)를 붙여주는 역할을 합니다.
 * 이 ID 덕분에 수많은 요청이 뒤섞여 로그가 찍혀도, 특정 사용자의 요청 흐름만 필터링해서 볼 수 있습니다.
 * </p>
 *
 * <h3>주요 기능</h3>
 * <ul>
 * <li><b>ID 부여:</b> 요청 헤더에 추적 ID가 없으면 새로 발급하고, 있으면 검증 후 재사용합니다.</li>
 * <li><b>MDC 연동:</b> 발급된 ID를 로거(Logger) 컨텍스트에 담아, 이후 모든 로그에 자동으로 출력되게 합니다.</li>
 * <li><b>응답 헤더:</b> 클라이언트에게도 이 ID를 돌려주어, 오류 발생 시 고객이 ID로 문의할 수 있게 합니다.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // [중요] 모든 보안 필터나 비즈니스 로직보다 '가장 먼저' 실행되어야 놓치는 로그가 없습니다.
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    // [보안] 외부에서 조작된 이상한 ID(스크립트 주입 등)가 들어오는 것을 막기 위한 정규식입니다.
    // - 허용: 영문 대소문자, 숫자, 하이픈(-)
    // - 길이: 1~50자 (너무 긴 ID는 메모리 문제 유발 가능)
    private static final Pattern CORRELATION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-]{1,50}$");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 1. 추적 ID 결정: 클라이언트가 보낸 ID를 쓸지, 새로 만들지 결정합니다.
        final String correlationId = resolveCorrelationId(request);

        // 2. MDC에 저장: 이제부터 이 스레드에서 발생하는 모든 로그(log.info 등)에는 이 ID가 함께 기록됩니다.
        MDC.put(MDC_KEY, correlationId);

        // 3. 응답 헤더 설정: 클라이언트(프론트엔드 등)도 현재 처리된 요청의 ID를 알 수 있게 돌려줍니다.
        // 'setHeader'를 사용하여 혹시라도 중복된 헤더가 쌓이는 것을 방지합니다.
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            // 4. 다음 단계 진행: 실제 비즈니스 로직(Controller 등)으로 요청을 넘깁니다.
            filterChain.doFilter(request, response);
        } finally {
            // 5. 뒷정리 (MDC 초기화)
            // 톰캣 같은 서버는 성능을 위해 스레드를 폐기하지 않고 재사용(Thread Pool)합니다.
            // 만약 여기서 ID를 지우지 않으면, 다음 요청을 처리할 때 이전 요청의 ID가 남아있는 '데이터 오염'이 발생합니다.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 요청 헤더를 분석하여 안전한 Correlation ID를 반환합니다.
     *
     * @param request HTTP 요청 객체
     * @return 검증된 기존 ID 또는 새로 생성된 UUID
     */
    private String resolveCorrelationId(HttpServletRequest request) {
        String headerId = request.getHeader(CORRELATION_ID_HEADER);

        // 1. 헤더에 ID가 있고 + 보안 패턴을 통과했다면? -> 그 ID를 그대로 사용 (시스템 간 연계 추적용)
        if (headerId != null && CORRELATION_ID_PATTERN.matcher(headerId).matches()) {
            return headerId;
        }

        // 2. ID가 없거나 이상한 값이라면? -> 충돌 확률이 극히 낮은 랜덤값(UUID) 생성
        return UUID.randomUUID().toString();
    }
}