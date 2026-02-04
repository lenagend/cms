package com.mingchico.cms.core.menu.domain;

import com.mingchico.cms.core.common.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * <h3>[메뉴 (Menu)]</h3>
 * <p>
 * 사이트의 계층형 메뉴 구조와 접근 권한(ACL)을 정의합니다.
 * URL 패턴 매칭을 통해 <b>보안(Security)</b>과 <b>내비게이션(Navigation)</b>의 기준이 됩니다.
 * </p>
 */
@Entity
@Table(name = "menus", indexes = {
        @Index(name = "idx_menu_url", columnList = "url_pattern"),
        @Index(name = "idx_menu_site", columnList = "site_code")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("소속 사이트 코드 (공통 메뉴인 경우 'COMMON' 등)")
    @Column(name = "site_code", nullable = false)
    private String siteCode;

    @Comment("상위 메뉴 ID (Null이면 최상위 루트)")
    @Column(name = "parent_id")
    private Long parentId;

    @Comment("메뉴명")
    @Column(nullable = false)
    private String name;

    @Comment("URL 패턴 (예: /board/notice/**)")
    @Column(name = "url_pattern", nullable = false)
    private String urlPattern;

    @Comment("메뉴 유형 (PAGE, BOARD, LINK, GROUP)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MenuType type;

    @Comment("출력 순서")
    @Column(nullable = false)
    private int displayOrder;

    @Comment("사용 여부 (False 시 접근 차단)")
    @Column(nullable = false)
    private boolean enabled = true;

    // --- [Security ACL] ---

    @Comment("조회 권한 (예: ANONYMOUS, ROLE_USER, ROLE_ADMIN)")
    @Column(name = "read_role", nullable = false)
    private String readRole;

    @Comment("쓰기/수정 권한 (게시판 등에서 사용)")
    @Column(name = "write_role", nullable = false)
    private String writeRole;

    @Builder
    public Menu(String siteCode, Long parentId, String name, String urlPattern, 
                MenuType type, int displayOrder, String readRole, String writeRole) {
        this.siteCode = siteCode;
        this.parentId = parentId;
        this.name = name;
        this.urlPattern = urlPattern;
        this.type = type;
        this.displayOrder = displayOrder;
        this.readRole = (readRole != null) ? readRole : "ANONYMOUS";
        this.writeRole = (writeRole != null) ? writeRole : "ROLE_ADMIN";
    }
}