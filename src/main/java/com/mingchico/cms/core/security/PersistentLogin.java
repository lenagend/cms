package com.mingchico.cms.core.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <h3>[Remember-Me 토큰 엔티티]</h3>
 * <p>
 * <b>역할:</b>
 * 1. DDL Auto를 통해 'persistent_logins' 테이블을 자동 생성합니다.
 * 2. 추후 관리자 페이지나 마이페이지에서 '현재 로그인된 기기 목록'을 조회/삭제할 때 사용합니다.
 * </p>
 * <p>
 * <b>주의:</b>
 * 실제 로그인 인증 시점에는 Spring Security의 {@code JdbcTokenRepositoryImpl}이
 * 직접 SQL을 실행하므로 이 엔티티가 사용되지 않습니다. (하이브리드 운용)
 * </p>
 */
@Entity
@Table(name = "persistent_logins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersistentLogin {

    @Id
    @Column(length = 64)
    private String series;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(name = "last_used", nullable = false)
    private LocalDateTime lastUsed;
}