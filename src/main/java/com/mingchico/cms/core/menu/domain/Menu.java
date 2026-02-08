package com.mingchico.cms.core.menu.domain;

import com.mingchico.cms.core.common.BaseAuditEntity;
import com.mingchico.cms.core.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <h3>[메뉴 (Menu)]</h3>
 * <p>
 * 사이트의 <b>네비게이션 구조</b>, <b>URL 라우팅 규칙</b>, <b>접근 제어(ACL)</b>를 통합 관리합니다.
 * <br>
 * <b>설계 반영 사항:</b>
 * <ul>
 * <li><b>AntPathMatcher:</b> 정확한 URL 대신 패턴(예: /board/**)을 저장하여 유연한 매칭 지원</li>
 * <li><b>상태 분리:</b> 노출(visible)과 접근 허용(accessible)을 분리하여 '숨겨진 메뉴' 지원</li>
 * <li><b>확장성:</b> 'handler' 필드를 통해 특정 로직이나 컨트롤러를 동적으로 바인딩</li>
 * <li><b>성능:</b> @BatchSize를 적용하여 계층 구조 조회 시 N+1 문제 해결</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "menus", indexes = {
        @Index(name = "idx_menu_site_parent", columnList = "site_code, parent_id"),
        @Index(name = "idx_menu_url_pattern", columnList = "url_pattern")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("소속 사이트 코드 (멀티테넌트 격리 키)")
    @Column(name = "site_code", nullable = false, length = 50)
    private String siteCode;

    // --- [1. Navigation & Hierarchy] ---

    @Comment("상위 메뉴 ID (Root는 NULL)")
    @Column(name = "parent_id")
    private Long parentId;

    @Comment("메뉴명")
    @Column(nullable = false)
    private String name;

    @Comment("아이콘 클래스 (예: fa-solid fa-home)")
    private String icon;

    @Comment("출력 순서")
    @Column(nullable = false)
    private int displayOrder;

    // --- [2. Routing & Behavior] ---

    @Comment("URL 패턴 (Ant-Style, 예: /board/notice/**)")
    @Column(name = "url_pattern", nullable = false)
    private String urlPattern;

    @Comment("메뉴 유형 (PAGE, BOARD, LINK, GROUP)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuType type;

    @Comment("링크 타겟 (_SELF, _BLANK)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuTarget target = MenuTarget._SELF;

    @Comment("핸들러 바인딩 (예: board.notice, page.intro)")
    @Column(name = "handler", length = 100)
    private String handler;

    // --- [3. Status Flags] ---

    @Comment("네비게이션 노출 여부 (False여도 URL 접근은 가능할 수 있음)")
    @Column(nullable = false)
    private boolean visible = true;

    @Comment("접근 가능 여부 (False면 404/점검중 처리)")
    @Column(nullable = false)
    private boolean accessible = true;

    // --- [4. Security ACL (CSV Storage)] ---

    @Comment("조회 권한 목록 (CSV, 예: ANONYMOUS,ROLE_USER)")
    @Column(name = "read_roles", nullable = false, length = 200)
    private String readRoles;

    @Comment("쓰기 권한 목록 (CSV, 예: ROLE_ADMIN,ROLE_MANAGER)")
    @Column(name = "write_roles", nullable = false, length = 200)
    private String writeRoles;

    // --- [Relations] ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private Menu parent;

    // [성능 최적화] 하위 메뉴 조회 시 WHERE parent_id IN (...) 쿼리로 한 번에 조회
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder asc")
    private List<Menu> children = new ArrayList<>();

    @Comment("권한 판단 정책 (미래 확장용: isAdminOrOwner 등)")
    @Column(name = "access_policy", length = 100)
    private String accessPolicy;

    // [New] 메뉴별 상세 설정 (JSON)
    @Comment("메뉴별 상세 설정 (댓글여부, 페이징 등)")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "json")
    private MenuConfig config;

    /**
     * [피드백 2번 수용] 단순 Role 체크를 넘어 복합 정책을 지원하기 위한 판단 메서드
     */
    public boolean isAuthorized(User user, String action) {
        if ("READ".equals(action) && getReadRoleSet().contains("ANONYMOUS")) return true;
        // 향후 여기에 PolicyEvaluator를 연동하여 복합 로직(본인 글 여부 등) 처리 가능
        return false;
    }

    @Builder
    public Menu(String siteCode, Long parentId, String name, String urlPattern, String handler,
                MenuType type, MenuTarget target, String icon, int displayOrder,
                boolean visible, boolean accessible, String readRoles, String writeRoles, MenuConfig config) {
        this.siteCode = siteCode;
        this.parentId = parentId;
        this.name = name;
        this.urlPattern = urlPattern;
        this.handler = handler;
        this.type = type;
        this.target = (target != null) ? target : MenuTarget._SELF;
        this.icon = icon;
        this.displayOrder = displayOrder;
        this.visible = visible;
        this.accessible = accessible;
        // 권한 없을 시 기본값 할당 (NPE 방지)
        this.readRoles = StringUtils.hasText(readRoles) ? readRoles : "ANONYMOUS";
        this.writeRoles = StringUtils.hasText(writeRoles) ? writeRoles : "ROLE_ADMIN";
        this.config = (config != null) ? config : new MenuConfig();
    }

    // --- [Helper Methods: CSV <-> Set] ---

    /**
     * DB에 저장된 CSV 문자열을 Set으로 변환하여 반환합니다.
     * Interceptor에서 권한 체크 시 이 메서드를 사용합니다.
     */
    public Set<String> getReadRoleSet() {
        return parseRoles(this.readRoles);
    }

    public Set<String> getWriteRoleSet() {
        return parseRoles(this.writeRoles);
    }

    private Set<String> parseRoles(String roleCsv) {
        if (!StringUtils.hasText(roleCsv)) return Collections.emptySet();
        return Arrays.stream(roleCsv.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    // --- [Business Methods] ---

    public void updateInfo(String name, String urlPattern, String handler, MenuType type,
                           MenuTarget target, String icon, int displayOrder,
                           boolean visible, boolean accessible) {
        this.name = name;
        this.urlPattern = urlPattern;
        this.handler = handler;
        this.type = type;
        this.target = target;
        this.icon = icon;
        this.displayOrder = displayOrder;
        this.visible = visible;
        this.accessible = accessible;
    }

    public void updateAcl(String readRoles, String writeRoles) {
        this.readRoles = readRoles;
        this.writeRoles = writeRoles;
    }

    public void updateConfig(MenuConfig config) {
        this.config = config;
    }
}