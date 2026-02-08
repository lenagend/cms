package com.mingchico.cms.core.security;

import java.util.Optional;

/**
 * <h3>[보안 컨텍스트 추상화]</h3>
 * <p>
 * 현재 접속자의 인증 상태와 권한 정보를 제공하는 인터페이스입니다.
 * 정적 유틸리티인 ContextHolder에 대한 직접 의존을 끊고,
 * 향후 SSO, JWT, 외부 IAM 도입 시 구현체 교체만으로 대응 가능하게 합니다.
 * </p>
 */
public interface AccessContext {
    
    /** 현재 사용자 이메일 반환 */
    Optional<String> getCurrentUserEmail();

    /** 로그인 여부 확인 */
    boolean isAuthenticated();

    /** 특정 권한 보유 여부 확인 */
    boolean hasRole(String role);

    /** 관리자 여부 확인 */
    default boolean isAdmin() {
        return hasRole("ADMIN");
    }
}