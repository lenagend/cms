package com.mingchico.cms.core.menu.repository;

import com.mingchico.cms.core.menu.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    /**
     * [전체 메뉴 조회 - 캐싱용]
     * 특정 사이트의 모든 메뉴 구조를 가져옵니다.
     * Interceptor는 이 데이터를 메모리(Caffeine/Redis)에 올린 뒤,
     * AntPathMatcher를 사용해 메모리 상에서 고속으로 URL 매칭을 수행합니다.
     */
    List<Menu> findAllBySiteCodeOrderByParentIdAscDisplayOrderAsc(String siteCode);

    /**
     * [네비게이션 렌더링용]
     * 실제 화면 상단/좌측 메뉴바를 그릴 때 사용합니다.
     * '숨김 처리(visible=false)'된 메뉴는 제외하고 가져옵니다.
     */
    List<Menu> findAllBySiteCodeAndVisibleTrueOrderByParentIdAscDisplayOrderAsc(String siteCode);
    
    /**
     * [관리자용] 최상위 루트 메뉴만 조회 (트리 관리 화면 진입점)
     */
    List<Menu> findBySiteCodeAndParentIdIsNullOrderByDisplayOrderAsc(String siteCode);
}