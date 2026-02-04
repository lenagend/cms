package com.mingchico.cms.core.tenant.service;

import com.mingchico.cms.core.tenant.domain.Tenant;
import com.mingchico.cms.core.tenant.dto.TenantInfo;
import com.mingchico.cms.core.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h3>[테넌트 메타데이터 제공자]</h3>
 * <p>
 * 캐시 전략(Local vs Redis)을 사용하여
 * DB 부하 없이 테넌트 정보를 초고속으로 제공합니다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantMetadataProvider {

    private final TenantRepository tenantRepository;

    /**
     * [조회 메서드]
     * 'tenant_meta'라는 이름은 application.yml의 policies에 정의되어 있습니다.
     * 특정 cacheManager를 지정하지 않아도, CacheConfig의 @Primary 빈이 자동으로 적용됩니다.
     */
    @Cacheable(value = "tenant_meta", key = "#siteCode") // [수정] cacheManager 속성 제거
    public TenantInfo getTenantInfo(String siteCode) {
        return tenantRepository.findBySiteCode(siteCode)
                .map(this::mapToInfo)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Site Code: " + siteCode));
    }

    /**
     * [캐시 삭제]
     */
    @CacheEvict(value = "tenant_meta", key = "#siteCode") // [수정] cacheManager 속성 제거
    public void evictCache(String siteCode) {
        // AOP 처리
    }

    private TenantInfo mapToInfo(Tenant tenant) {
        return new TenantInfo(
                tenant.getId(),
                tenant.getSiteCode(),
                tenant.getName(),
                tenant.getThemeName(),
                tenant.isMaintenance(),
                tenant.isReadOnly()
        );
    }
}