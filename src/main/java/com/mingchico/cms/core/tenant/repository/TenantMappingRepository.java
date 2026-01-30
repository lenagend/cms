package com.mingchico.cms.core.tenant.repository;

import com.mingchico.cms.core.tenant.domain.TenantMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * <h3>[테넌트 매핑 리포지토리]</h3>
 * <p>
 * {@link TenantMapping} 엔티티에 대한 데이터베이스 접근을 담당합니다.
 * </p>
 */
@Repository
public interface TenantMappingRepository extends JpaRepository<TenantMapping, Long> {

    /**
     * 특정 도메인 패턴이 이미 등록되어 있는지 확인합니다. (중복 등록 방지)
     *
     * @param domainPattern 확인할 도메인 패턴 (예: "a.com")
     * @return 존재 여부
     */
    boolean existsByDomainPattern(String domainPattern);

    /**
     * 모든 테넌트 매핑 규칙을 도메인 패턴의 역순(Descending)으로 조회합니다.
     * <p>
     * <b>이유:</b> 와일드카드(*)가 포함된 패턴보다 구체적인 도메인(www.a.com)이
     * 문자열 정렬 시 우선순위를 갖게 하여 매칭 정확도를 높이기 위함입니다.
     * </p>
     *
     * @return 정렬된 테넌트 매핑 목록
     */
    List<TenantMapping> findAllByOrderByDomainPatternDesc();

    /**
     * 도메인 패턴으로 단건을 조회합니다.
     *
     * @param domainPattern 조회할 패턴
     * @return 테넌트 매핑 정보 (Optional)
     */
    Optional<TenantMapping> findByDomainPattern(String domainPattern);
}