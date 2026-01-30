package com.mingchico.cms.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * <h3>[JPA Auditing 설정]</h3>
 * <p>
 * 엔티티의 생성일/수정일 및 생성자/수정자를 자동 주입하는 기능을 활성화합니다.
 * </p>
 */
@Configuration
// auditorAwareRef: 아래 정의한 빈(Bean) 이름을 연결합니다.
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditConfig {

    /**
     * [현재 사용자 식별자 제공자]
     * @CreatedBy, @LastModifiedBy가 붙은 필드에 넣을 값을 결정합니다.
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            // 1. 스프링 시큐리티에서 인증 정보 가져오기
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // 2. 인증 정보가 없거나, 익명 사용자(로그인 안 함)인 경우 처리
            if (authentication == null ||
                    !authentication.isAuthenticated() ||
                    "anonymousUser".equals(authentication.getPrincipal())) {

                // 로그인 없이 시스템이 자동으로 생성하는 경우(초기 데이터 등) "SYSTEM"으로 기록
                // 혹은 null을 리턴하여 DB에 null로 남길 수도 있음
                return Optional.of("SYSTEM");
            }

            // 3. 로그인한 사용자의 이름(Principal) 반환
            // UserDetails 구현체에 따라 getName()이 ID가 됩니다.
            return Optional.ofNullable(authentication.getName());
        };
    }
}