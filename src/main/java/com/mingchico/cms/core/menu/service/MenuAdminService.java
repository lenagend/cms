package com.mingchico.cms.core.menu.service;

import com.mingchico.cms.core.menu.domain.Menu;
import com.mingchico.cms.core.menu.dto.MenuDto;
import com.mingchico.cms.core.menu.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <h3>[메뉴 관리자 서비스]</h3>
 * <p>
 * 사이트의 메뉴 구조와 보안 정책(ACL)을 관리합니다.
 * 메뉴 변경 시 <b>'menu_list'</b> 캐시를 무효화하여 실시간 반영을 보장합니다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuAdminService {

    private final MenuRepository menuRepository;

    /**
     * [조회] 특정 사이트의 전체 메뉴 트리를 조회합니다.
     */
    public List<MenuDto.Response> getMenuTree(String siteCode) {
        // 루트 메뉴만 조회하면 @BatchSize에 의해 자식들이 효율적으로 로딩됨
        return menuRepository.findBySiteCodeAndParentIdIsNullOrderByDisplayOrderAsc(siteCode)
                .stream()
                .map(MenuDto.Response::from)
                .collect(Collectors.toList());
    }

    /**
     * [생성] 신규 메뉴를 등록합니다.
     */
    @Transactional
    @CacheEvict(value = "menu_list", key = "#request.siteCode()")
    public Long createMenu(MenuDto.SaveRequest request) {
        log.info("✨ Creating new menu: [{}] for site: {}", request.name(), request.siteCode());

        Menu menu = Menu.builder()
                .siteCode(request.siteCode())
                .parentId(request.parentId())
                .name(request.name())
                .urlPattern(request.urlPattern())
                .type(request.type())
                .handler(request.handler())
                .target(request.target())
                .icon(request.icon())
                .displayOrder(request.displayOrder())
                .visible(request.visible())
                .accessible(request.accessible())
                .readRoles(request.readRoles())
                .writeRoles(request.writeRoles())
                .config(request.config())
                .build();

        return menuRepository.save(menu).getId();
    }

    /**
     * [수정] 메뉴 정보 및 보안 정책을 업데이트합니다.
     */
    @Transactional
    @CacheEvict(value = "menu_list", key = "#request.siteCode()")
    public void updateMenu(Long id, MenuDto.SaveRequest request) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메뉴입니다. ID: " + id));

        // 1. 기본 정보 수정
        menu.updateInfo(
                request.name(),
                request.urlPattern(),
                request.handler(),
                request.type(),
                request.target(),
                request.icon(),
                request.displayOrder(),
                request.visible(),
                request.accessible()
        );

        // 2. ACL 수정
        menu.updateAcl(request.readRoles(), request.writeRoles());

        // 3. 상세 설정(JSON) 수정
        // DTO의 config가 null일 경우를 대비해 처리 필요하다면 여기서 체크
        menu.updateConfig(request.config());

        log.info("✅ Menu Updated: [{}] (ID: {})", menu.getName(), id);
    }

    /**
     * [삭제] 메뉴를 삭제합니다.
     * CascadeType.ALL 설정으로 하위 메뉴도 함께 삭제됩니다.
     */
    @Transactional
    @CacheEvict(value = "menu_list", key = "#siteCode")
    public void deleteMenu(String siteCode, Long id) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메뉴입니다. ID: " + id));

        // 테넌트 격리 검증 (보안상 필수)
        if (!menu.getSiteCode().equals(siteCode)) {
            throw new IllegalStateException("해당 사이트의 메뉴가 아닙니다.");
        }

        menuRepository.delete(menu);
        log.info("🗑️ Menu and its children deleted. ID: {}", id);
    }

    /**
     * [순서 변경] 여러 메뉴의 정렬 순서를 일괄 조정합니다. (Drag & Drop 대응)
     */
    @Transactional
    @CacheEvict(value = "menu_list", key = "#siteCode")
    public void reorderMenus(String siteCode, List<Long> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            int order = i + 1;
            menuRepository.findById(id).ifPresent(m -> {
                // 이 메서드는 엔티티에 별도로 구현하거나 직접 필드 수정
                // 예: m.updateOrder(order);
            });
        }
    }
}