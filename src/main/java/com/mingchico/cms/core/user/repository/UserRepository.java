package com.mingchico.cms.core.user.repository;

import com.mingchico.cms.core.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * <h3>[사용자 리포지토리]</h3>
 * <p>
 * 시큐리티 인증을 위한 핵심 조회 메서드를 제공합니다.
 * </p>
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    /**
     * 이메일 중복 체크
     */
    boolean existsByEmail(String email);
}