package com.mingchico.cms.core.tenant.service;

import com.mingchico.cms.core.tenant.DomainTenantResolver;
import com.mingchico.cms.core.tenant.domain.TenantMapping;
import com.mingchico.cms.core.tenant.repository.TenantMappingRepository;
import com.mingchico.cms.core.tenant.dto.TenantDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <h3>[테넌트 관리 서비스]</h3>
 * <p>
 * 관리자(Admin) 기능을 수행하는 비즈니스 로직입니다.
 * DB 업데이트뿐만 아니라, 실시간 반영을 위해 {@link DomainTenantResolver}의 캐시 갱신도 트리거합니다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantAdminService {

    private final TenantMappingRepository repository;
    private final DomainTenantResolver domainTenantResolver; // 캐시 갱신용

    /**
     * 전체 테넌트 목록 조회
     */
    public List<TenantDto.Response> getAllTenants() {
        return repository.findAll().stream()
                .map(TenantDto.Response::from)
                .toList();
    }

    /**
     * 테넌트 등록
     */
    @Transactional
    public TenantDto.Response createTenant(TenantDto.CreateRequest request) {
        // 1. 중복 검사
        if (repository.existsByDomainPattern(request.domainPattern())) { // Repository에 existsBy... 추가 필요
            throw new IllegalArgumentException("이미 등록된 도메인 패턴입니다: " + request.domainPattern());
        }

        // 2. 저장
        TenantMapping saved = repository.save(TenantMapping.builder()
                .domainPattern(request.domainPattern())
                .siteCode(request.siteCode())
                .description(request.description())
                .build());

        log.info("New Tenant Created: {} -> {}", request.domainPattern(), request.siteCode());

        // 3. 캐시 즉시 갱신 (운영자가 등록하자마자 바로 접속 가능하게)
        domainTenantResolver.refreshRules();

        return TenantDto.Response.from(saved);
    }

    /**
     * 테넌트 수정
     */
    @Transactional
    public TenantDto.Response updateTenant(Long id, TenantDto.UpdateRequest request) {
        TenantMapping tenant = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 테넌트 ID입니다."));

        // 엔티티에 update 메서드 추가 필요 (Setter 지양)
        tenant.update(request.siteCode(), request.description());

        log.info("Tenant Updated: ID {} -> {}", id, request.siteCode());

        // 캐시 즉시 갱신
        domainTenantResolver.refreshRules();

        return TenantDto.Response.from(tenant);
    }

    /**
     * 테넌트 삭제
     */
    @Transactional
    public void deleteTenant(Long id) {
        TenantMapping tenant = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 테넌트 ID입니다."));

        repository.delete(tenant);
        log.info("Tenant Deleted: {}", tenant.getDomainPattern());

        // 캐시 즉시 갱신
        domainTenantResolver.refreshRules();
    }
}