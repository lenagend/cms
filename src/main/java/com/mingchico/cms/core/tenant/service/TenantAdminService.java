package com.mingchico.cms.core.tenant.service;

import com.mingchico.cms.core.tenant.domain.Tenant;
import com.mingchico.cms.core.tenant.dto.TenantDto;
import com.mingchico.cms.core.tenant.event.TenantRouteChangedEvent;
import com.mingchico.cms.core.tenant.repository.TenantRepository;
import com.mingchico.cms.core.theme.ThemeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * <h3>[테넌트 관리자 서비스]</h3>
 * <p>
 * 테넌트 생성, 수정, 삭제를 담당합니다.
 * </p>
 * <h3>[변경 사항]</h3>
 * <ul>
 * <li><b>Decoupling:</b> DomainTenantResolver 의존성을 제거하고 이벤트 발행 방식으로 변경했습니다.</li>
 * <li><b>Optimization:</b> 도메인 패턴이 변경되지 않는 단순 정보 수정(Update) 시에는 라우팅 재설정을 하지 않습니다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantAdminService {

    private final TenantRepository tenantRepository;
    private final TenantMetadataProvider tenantMetadataProvider; // 메타데이터 캐시 관리
    private final ApplicationEventPublisher eventPublisher;      // 이벤트 발행기
    private final ThemeProperties themeProperties;

    public List<TenantDto.Response> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(TenantDto.Response::from)
                .toList();
    }

    @Transactional
    public TenantDto.Response createTenant(TenantDto.CreateRequest request) {
        validateUniqueTenant(request);

        String themeToCheck = StringUtils.hasText(request.themeName()) ? request.themeName() : "default";
        validateThemeExists(themeToCheck);

        Tenant saved = tenantRepository.save(Tenant.builder()
                .domainPattern(request.domainPattern())
                .siteCode(request.siteCode())
                .name(request.name())
                .description(request.description())
                .themeName(themeToCheck)
                .features(request.features())
                .build());

        log.info("New Tenant Created: {} -> {}", request.domainPattern(), request.siteCode());

        // [Event] 새 도메인이 생겼으니 라우팅 규칙을 갱신하라고 알림
        eventPublisher.publishEvent(new TenantRouteChangedEvent());

        return TenantDto.Response.from(saved);
    }

    @Transactional
    public TenantDto.Response updateTenant(Long id, TenantDto.UpdateRequest request) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 테넌트 ID입니다."));

        // [Safety] 테마 변경 시 검증
        if (StringUtils.hasText(request.themeName())) {
            validateThemeExists(request.themeName());
        }

        // 테넌트 정보 업데이트 (JSON Features 포함)
        tenant.updateConfig(
                request.themeName(),
                request.features()
        );

        tenant.update(request.name(), request.description(), request.themeName());

        // [Cache Evict] 메타데이터 갱신
        tenantMetadataProvider.evictCache(tenant.getSiteCode());

        log.info("Tenant Updated: ID {}", id);
        return TenantDto.Response.from(tenant);
    }

    @Transactional
    public void deleteTenant(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 테넌트 ID입니다."));

        String siteCode = tenant.getSiteCode();
        tenantRepository.delete(tenant);

        // [Cache] 메타데이터 삭제
        tenantMetadataProvider.evictCache(siteCode);

        // [Event] 도메인이 사라졌으니 라우팅 규칙 갱신 알림
        eventPublisher.publishEvent(new TenantRouteChangedEvent());

        log.info("Tenant Deleted: {}", tenant.getDomainPattern());
    }

    private void validateUniqueTenant(TenantDto.CreateRequest request) {
        if (tenantRepository.existsByDomainPattern(request.domainPattern())) {
            throw new IllegalArgumentException("이미 등록된 도메인 패턴입니다: " + request.domainPattern());
        }
        if (tenantRepository.existsBySiteCode(request.siteCode())) {
            throw new IllegalArgumentException("이미 존재하는 사이트 코드입니다: " + request.siteCode());
        }
    }

    /**
     * [테마 검증 로직]
     * yml 설정에 없는 테마 코드가 들어오면 예외를 발생시킵니다.
     */
    private void validateThemeExists(String themeCode) {
        if (!themeProperties.isValidTheme(themeCode)) {
            throw new IllegalArgumentException("지원하지 않는 테마 코드입니다: " + themeCode);
        }
    }
}