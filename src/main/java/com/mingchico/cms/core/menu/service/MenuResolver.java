package com.mingchico.cms.core.menu.service;

import com.mingchico.cms.core.menu.domain.Menu;
import com.mingchico.cms.core.menu.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * <h3>[메뉴 리졸버 (Menu Resolver)]</h3>
 * <p>
 * 요청된 URL을 분석하여 가장 적합한 메뉴(Menu) 엔티티를 찾아냅니다.
 * DB 부하를 제로(0)로 만들기 위해 전체 메뉴 트리를 캐싱하고,
 * 메모리 상에서 {@link AntPathMatcher}를 돌려 실시간으로 매칭합니다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuResolver {

    private final MenuRepository menuRepository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * [핵심 로직] URL로 메뉴 찾기
     * <p>
     * 1. 해당 사이트(Tenant)의 모든 메뉴를 캐시에서 가져옵니다.
     * 2. AntPathMatcher로 URL 패턴을 검사합니다.
     * 3. 여러 개가 매칭되면 <b>"가장 구체적인 패턴(길이가 긴 것)"</b>을 선택합니다.
     * (예: /board/** vs /board/notice/** 중 후자 선택)
     * </p>
     */
    public Optional<Menu> resolve(String siteCode, String requestUri) {
        List<Menu> allMenus = getCachedMenus(siteCode);

        return allMenus.stream()
                // 1. URL 패턴 매칭 (Ant-Style)
                .filter(menu -> pathMatcher.match(menu.getUrlPattern(), requestUri))
                // 2. 가장 구체적인 패턴 우선 (Longest Path Win Strategies)
                .max(Comparator.comparingInt(menu -> menu.getUrlPattern().length()));
    }

    /**
     * [캐시 계층] 사이트별 메뉴 목록 캐싱
     * <p>
     * 'menu_list' 캐시는 메뉴 추가/수정/삭제 시에만 갱신(Evict)됩니다.
     * 운영 중에는 DB 쿼리가 전혀 발생하지 않습니다.
     * </p>
     */
    @Cacheable(value = "menu_list", key = "#siteCode")
    public List<Menu> getCachedMenus(String siteCode) {
        log.debug("⚡ Loading menus from DB for site: {}", siteCode);
        // 접근 가능(Accessible)하지 않은 메뉴는 아예 로딩하지 않을 수도 있지만,
        // 관리 목적이나 404/403 분기를 위해 전체를 로딩하는 것이 유리합니다.
        return menuRepository.findAllBySiteCodeOrderByParentIdAscDisplayOrderAsc(siteCode);
    }
}