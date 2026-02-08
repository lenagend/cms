package com.mingchico.cms.core.menu;

import com.mingchico.cms.core.menu.domain.Menu;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * <h3>[메뉴 컨텍스트 (Menu Context)]</h3>
 * <p>
 * 현재 요청(Request)이 어떤 메뉴 경로를 타고 들어왔는지에 대한 정보를
 * {@link ThreadLocal}에 저장하여 전역적으로 공유합니다.
 * <br>
 * 컨트롤러나 뷰(View)에서 "현재 메뉴 이름", "현재 메뉴의 핸들러" 등을
 * 파라미터 전달 없이 즉시 조회할 수 있게 합니다.
 * </p>
 */
@Slf4j
public class MenuContext {

    private static final ThreadLocal<Menu> CURRENT_MENU = new ThreadLocal<>();

    public static void set(Menu menu) {
        if (menu != null) {
            CURRENT_MENU.set(menu);
            log.trace("✅ Menu Context Bound: [{}] {}", menu.getId(), menu.getName());
        }
    }

    public static Optional<Menu> getCurrentMenu() {
        return Optional.ofNullable(CURRENT_MENU.get());
    }

    public static void clear() {
        CURRENT_MENU.remove();
    }
}