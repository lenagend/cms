package com.mingchico.cms.core.menu;

import com.mingchico.cms.core.menu.domain.Menu;
import com.mingchico.cms.core.menu.domain.MenuType;
import com.mingchico.cms.core.menu.repository.MenuRepository;
import com.mingchico.cms.core.tenant.DomainTenantResolver;
import com.mingchico.cms.core.tenant.domain.Tenant;
import com.mingchico.cms.core.tenant.repository.TenantRepository;
import com.mingchico.cms.core.user.repository.MembershipRepository;
import com.mingchico.cms.core.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MenuSystemIntegrationTest.TestController.class)
class MenuSystemIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MenuRepository menuRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private MembershipRepository membershipRepository; // [필수] FK 제약 해제용
    @Autowired private UserRepository userRepository; // [권장] 깔끔한 User 정리를 위해 추가
    @Autowired private DomainTenantResolver domainTenantResolver;
    @Autowired private CacheManager cacheManager;

    private final String SITE_HOST = "menu-test.localhost";
    private final String SITE_CODE = "MENU_TEST_SITE";

    @BeforeEach
    void setUp() {
        // 1. 캐시 초기화
        if (cacheManager.getCache("menu_list") != null) {
            Objects.requireNonNull(cacheManager.getCache("menu_list")).clear();
        }

        // 2. 데이터 클렌징 (순서 매우 중요!)
        // 자식 테이블(Membership) -> 부모 테이블(Tenant, User) 순으로 지워야 FK 에러가 안 납니다.
        membershipRepository.deleteAllInBatch(); // delete from memberships
        menuRepository.deleteAllInBatch();       // delete from menus
        tenantRepository.deleteAllInBatch();     // delete from tenants
        userRepository.deleteAllInBatch();       // delete from users

        // 3. 테스트용 테넌트 생성
        tenantRepository.save(Tenant.builder()
                .siteCode(SITE_CODE)
                .domainPattern(SITE_HOST)
                .name("메뉴 테스트 사이트")
                .themeName("default")
                .build());

        // 4. 테넌트 리졸버 갱신
        domainTenantResolver.refreshRules();

        // 5. 메뉴 데이터 시나리오 셋업
        setupMenuData();
    }

    private void setupMenuData() {
        // Case A: 광범위한 패턴
        Menu parentBoard = Menu.builder()
                .siteCode(SITE_CODE)
                .name("게시판 홈")
                .urlPattern("/board/**")
                .type(MenuType.BOARD)
                .displayOrder(1)
                .visible(true).accessible(true)
                .readRoles("ANONYMOUS")
                .build();
        menuRepository.save(parentBoard);

        // Case B: 구체적인 패턴 (우선순위 높음)
        Menu noticeBoard = Menu.builder()
                .siteCode(SITE_CODE)
                .parentId(parentBoard.getId())
                .name("공지사항")
                .urlPattern("/board/notice/**")
                .type(MenuType.BOARD)
                .displayOrder(1)
                .visible(true).accessible(true)
                .readRoles("ANONYMOUS")
                .build();
        menuRepository.save(noticeBoard);

        // Case C: 관리자 전용
        Menu adminMenu = Menu.builder()
                .siteCode(SITE_CODE)
                .name("관리자 페이지")
                .urlPattern("/admin/**")
                .type(MenuType.PAGE)
                .displayOrder(2)
                .visible(true).accessible(true)
                .readRoles("ROLE_ADMIN")
                .build();
        menuRepository.save(adminMenu);

        // Case D: 접근 불가
        Menu hiddenEvent = Menu.builder()
                .siteCode(SITE_CODE)
                .name("비공개 이벤트")
                .urlPattern("/event/secret/**")
                .type(MenuType.PAGE)
                .displayOrder(3)
                .visible(false).accessible(false)
                .build();
        menuRepository.save(hiddenEvent);
    }

    @Test
    @DisplayName("[Resolver] 구체적인 URL 패턴이 우선순위를 가져야 한다")
    void resolve_specific_pattern_priority() throws Exception {
        mockMvc.perform(get("/board/notice/1")
                        .header("Host", SITE_HOST))
                .andExpect(status().isOk())
                .andExpect(request().attribute("currentMenu", org.hamcrest.Matchers.hasProperty("name", org.hamcrest.Matchers.is("공지사항"))));
    }

    @Test
    @DisplayName("[Resolver] 구체적인 패턴이 없을 땐 광범위한 패턴이 잡아야 한다")
    void resolve_general_pattern() throws Exception {
        mockMvc.perform(get("/board/free/1")
                        .header("Host", SITE_HOST))
                .andExpect(status().isOk())
                .andExpect(request().attribute("currentMenu", org.hamcrest.Matchers.hasProperty("name", org.hamcrest.Matchers.is("게시판 홈"))));
    }

    @Test
    @DisplayName("[ACL] 권한이 부족한 유저는 접근이 거부되어야 한다 (403)")
    @WithMockUser(username = "user", roles = "USER")
    void access_denied_for_insufficient_role() throws Exception {
        mockMvc.perform(get("/admin/dashboard")
                        .header("Host", SITE_HOST))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[Status] accessible=false 인 메뉴는 404를 반환해야 한다")
    void access_to_inaccessible_menu_returns_404() throws Exception {
        mockMvc.perform(get("/event/secret/entry")
                        .header("Host", SITE_HOST))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[Context] 요청 종료 후 MenuContext는 비워져야 한다")
    void menu_context_cleanup() throws Exception {
        mockMvc.perform(get("/board/notice/1")
                        .header("Host", SITE_HOST))
                .andExpect(status().isOk());

        assertThat(MenuContext.getCurrentMenu()).isEmpty();
    }

    @RestController
    static class TestController {
        @GetMapping("/board/**") public String board() { return "board"; }
        @GetMapping("/admin/**") public String admin() { return "admin"; }
        @GetMapping("/event/**") public String event() { return "event"; }
    }
}