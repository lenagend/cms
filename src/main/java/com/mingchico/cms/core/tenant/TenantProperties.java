package com.mingchico.cms.core.tenant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * <h3>[테넌트 설정 프로퍼티]</h3>
 * <p>
 * {@code application.yml}의 'cms.tenant' 설정을 매핑합니다.
 * <br>
 * <b>기본값 전략:</b> 설정 파일에 별도로 명시하지 않아도,
 * 시스템 운영에 필수적인 경로(로그인, 정적파일, H2콘솔 등)는 기본적으로 제외 목록에 포함시킵니다.
 * </p>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cms.tenant")
public class TenantProperties {

    /**
     * [테넌트 식별 제외 경로 목록]
     * 이 경로 패턴에 매칭되는 요청은 TenantFilter가 사이트 코드를 검사하지 않고 통과시킵니다.
     * <p>
     * 기본값으로 로그인, 정적 리소스, 관리자 API, H2 콘솔 등이 포함되어 있어
     * 개발자가 yml에 일일이 적지 않아도 즉시 테스트가 가능합니다.
     * </p>
     */
    private List<String> excludedPaths = List.of(
            "/login",              // 로그인 페이지
            "/login-process",      // 로그인 처리 URL
            "/find-password",      // 비밀번호 찾기
            "/register",           // 회원가입
            "/favicon.ico",        // 파비콘
            "/error",              // 스프링 부트 기본 에러 페이지
            "/static/**",          // 정적 리소스 (css, js, images)
            "/actuator/**",        // 헬스 체크 및 메트릭
            "/api/admin/**",       // 관리자 API (Security가 별도 제어)
            "/h2-console/**"       // H2 DB 콘솔 (개발용)
    );
}