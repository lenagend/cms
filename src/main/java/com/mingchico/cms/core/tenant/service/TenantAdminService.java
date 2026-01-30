package com.mingchico.cms.core.tenant.service;

import com.mingchico.cms.core.tenant.DomainTenantResolver;
import com.mingchico.cms.core.tenant.domain.Tenant;
import com.mingchico.cms.core.tenant.dto.TenantDto;
import com.mingchico.cms.core.tenant.repository.TenantRepository; // [수정] import 변경
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantAdminService {

    private final TenantRepository tenantRepository;
    private final DomainTenantResolver domainTenantResolver;

    public List<TenantDto.Response> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(TenantDto.Response::from)
                .toList();
    }

    @Transactional
    public TenantDto.Response createTenant(TenantDto.CreateRequest request) {
        // 1. 중복 검사 (도메인 & 사이트코드)
        if (tenantRepository.existsByDomainPattern(request.domainPattern())) {
            throw new IllegalArgumentException("이미 등록된 도메인 패턴입니다: " + request.domainPattern());
        }
        // [추가] 사이트 코드도 유니크해야 하므로 검사 필요
        if (tenantRepository.existsBySiteCode(request.siteCode())) {
            throw new IllegalArgumentException("이미 존재하는 사이트 코드입니다: " + request.siteCode());
        }

        // 2. 저장
        Tenant saved = tenantRepository.save(Tenant.builder()
                .domainPattern(request.domainPattern())
                .siteCode(request.siteCode())
                .name(request.name())
                .description(request.description())
                .build());

        log.info("New Tenant Created: {} -> {}", request.domainPattern(), request.siteCode());

        // 3. 캐시 갱신
        domainTenantResolver.refreshRules();

        return TenantDto.Response.from(saved);
    }

    @Transactional
    public TenantDto.Response updateTenant(Long id, TenantDto.UpdateRequest request) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 테넌트 ID입니다."));

        tenant.update(
                request.name(),
                request.description()
        );

        log.info("Tenant Updated: ID {}", id);
        domainTenantResolver.refreshRules();

        return TenantDto.Response.from(tenant);
    }

    @Transactional
    public void deleteTenant(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 테넌트 ID입니다."));

        tenantRepository.delete(tenant);
        log.info("Tenant Deleted: {}", tenant.getDomainPattern());
        domainTenantResolver.refreshRules();
    }
}