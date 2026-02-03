package com.mingchico.cms.core.tenant.repository;

import com.mingchico.cms.core.tenant.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * <h3>[테넌트 리포지토리]</h3>
 * <p>
 * {@link Tenant} 엔티티(테넌트 정보, 도메인 연결 등)를 관리합니다.
 * </p>
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    // 도메인 중복 검사
    boolean existsByDomainPattern(String domainPattern);

    // 사이트 코드 중복 검사
    boolean existsBySiteCode(String siteCode);

    // 단건 조회
    Optional<Tenant> findByDomainPattern(String domainPattern);

    // 사이트 코드로 조회
    Optional<Tenant> findBySiteCode(String siteCode);
}