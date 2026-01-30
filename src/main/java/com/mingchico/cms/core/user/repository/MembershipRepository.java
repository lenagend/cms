package com.mingchico.cms.core.user.repository;

import com.mingchico.cms.core.user.domain.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    /**
     * [로그인 인증 쿼리]
     * 이메일(User)과 사이트코드(Tenant)를 기준으로 멤버십을 조회합니다.
     */
    @Query("SELECT m FROM Membership m " +
            "JOIN FETCH m.user u " +
            "JOIN FETCH m.tenant t " +
            "WHERE u.email = :email AND t.siteCode = :siteCode")
    Optional<Membership> findActiveMembership(@Param("email") String email,
                                              @Param("siteCode") String siteCode);
}