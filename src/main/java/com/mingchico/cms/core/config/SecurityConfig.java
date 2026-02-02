package com.mingchico.cms.core.config;

import com.mingchico.cms.core.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * <h3>[스프링 시큐리티 핵심 설정]</h3>
 * <p>
 * 상용 CMS 수준의 보안 정책을 정의합니다.
 * <b>개선점:</b>
 * 1. SecurityFilterChain을 API용과 WEB용으로 분리하여 유지보수성 및 성능 향상
 * 2. 강력한 보안 헤더(CSP, HSTS, Referrer 등) 적용
 * 3. SessionEventPublisher 등록을 통한 정확한 세션 제어
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
@EnableConfigurationProperties(SecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties properties;
    private final FormAuthenticationSuccessHandler successHandler;
    private final FormAuthenticationFailureHandler failureHandler;
    private final SmartAuthenticationEntryPoint authenticationEntryPoint;
    private final UserDetailsService userDetailsService;
    private final DataSource dataSource;

    // --- [Section 세션 관련 빈 정의] ---

    /**
     * [세션 레지스트리]
     * 현재 로그인된 사용자 목록을 관리합니다. (동시 접속 제어 필수)
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * [세션 이벤트 발행자]
     * Tomcat의 세션 생성/소멸 이벤트를 감지하여 Registry를 최신화합니다.
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * [복합 세션 인증 전략]
     * 로그인 성공 시 실행할 작업들을 순서대로 조립합니다.
     */
    @Bean
    public CompositeSessionAuthenticationStrategy sessionAuthenticationStrategy() {
        List<SessionAuthenticationStrategy> strategies = new ArrayList<>();

        // 1. 동시 접속 제어 (우리가 만든 커스텀 전략)
        strategies.add(new TenantAwareSessionStrategy(sessionRegistry(), properties));
        // 2. 세션 고정 보호 (로그인 시 세션 ID 교체)
        strategies.add(new SessionFixationProtectionStrategy());

        // 3. 레지스트리 등록 (필수)
        strategies.add(new RegisterSessionAuthenticationStrategy(sessionRegistry()));

        return new CompositeSessionAuthenticationStrategy(strategies);
    }

    /**
     * <h3>[1. API 전용 보안 체인]</h3>
     * <p>
     * - 대상: /api/** 경로
     * - 특징: Stateless, CSRF 비활성화, 세션 생성 안 함
     * - 목적: REST API 호출 시 불필요한 리소스 낭비를 줄이고 인증 구조를 단순화
     * </p>
     */
    @Bean
    @Order(1) // 우선순위 높음
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**") // 이 체인은 /api/** 요청만 처리

                // [API는 CSRF 불필요] REST API는 보통 세션 대신 토큰을 쓰거나,
                // 브라우저가 아닌 클라이언트(앱 등)에서 호출하므로 CSRF를 끕니다.
                .csrf(csrf -> csrf.disable())

                // [Stateless] 서버에 세션을 만들지 않습니다.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // [예외 처리] API 요청에 대한 인증 실패는 무조건 401 JSON 응답
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                )

                // [인가 정책]
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll() // 공개 API
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    /**
     * <h3>[2. WEB(CMS) 전용 보안 체인]</h3>
     * <p>
     * - 대상: 나머지 모든 경로 (/**)
     * - 특징: Session 기반, Form Login, CSRF 활성화, 보안 헤더 적용
     * - 목적: Thymeleaf 기반의 관리자/사용자 화면 보호
     * </p>
     */
    @Bean
    @Order(2) // API 체인 다음으로 실행
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
                //  보안 헤더 설정
                .headers(headers -> headers
                        // H2 Console 등 iframe 허용 (SameOrigin)
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                        // XSS 방어: 브라우저가 서버가 허용한 소스만 실행하도록 제한 (매우 중요)
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("script-src 'self' 'unsafe-inline'; object-src 'none'; base-uri 'self';")
                        )
                        // HTTPS 강제
                        // TODO: 프로덕션에서는 http-> https를 위해 주석해제
                        /*.httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )*/
                        // Referrer 정보 노출 최소화 (개인정보 보호)
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                        )
                )

                // [CSRF 설정] 브라우저 기반이므로 필수
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()) // JS에서 토큰 접근 허용 (AJAX 통신용)
                        .ignoringRequestMatchers(
                                new AntPathRequestMatcher("/h2-console/**") // H2 콘솔은 예외
                        )
                )

                // [인가 정책]
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers("/static/**", "/error", "/health", "/favicon.ico").permitAll()
                        .requestMatchers("/login", "/register", "/find-password").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )

                // [예외 처리] AJAX 요청인 경우 401 JSON, 아니면 로그인 페이지로 리다이렉트
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                )

                // [Form 로그인]
                .formLogin(login -> login
                        .loginPage("/login")
                        .loginProcessingUrl("/login-process")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll()
                )

                // [로그아웃]
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "remember-me", "XSRF-TOKEN")
                )

                // [자동 로그인 (Remember Me)]
                .rememberMe(remember -> remember
                        .key(properties.rememberMe().key())
                        .tokenValiditySeconds(properties.rememberMe().validitySeconds())
                        .tokenRepository(persistentTokenRepository())
                        .userDetailsService(userDetailsService)
                )

                // [세션 관리]
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionAuthenticationStrategy(sessionAuthenticationStrategy())
                );

        return http.build();
    }


    /**
     * [JDBC 토큰 저장소]
     * PersistentLogin 엔티티와 매핑되는 테이블을 사용합니다.
     * *주의: 운영 환경에서는 Flyway 등으로 'persistent_logins' 테이블 스키마를 미리 생성해두어야 합니다.
     */
    @Bean
    public PersistentTokenRepository persistentTokenRepository() {
        JdbcTokenRepositoryImpl repo = new JdbcTokenRepositoryImpl();
        repo.setDataSource(dataSource);
        return repo;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}