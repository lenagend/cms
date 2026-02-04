package com.mingchico.cms.core.tenant.domain;

import com.mingchico.cms.core.common.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

/**
 * <h3>[테넌트 (Tenant)]</h3>
 * <p>
 * 하나의 서비스 공간(워크스페이스, 사이트)을 정의하는 최상위 엔티티입니다.
 * 도메인 연결 정보와 사이트별 정책(Policy)을 관리합니다.
 * </p>
 */
@Entity
@Table(name = "tenants", indexes = {
        @Index(name = "idx_tenant_domain", columnList = "domain_pattern", unique = true),
        @Index(name = "idx_tenant_site_code", columnList = "site_code", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("사이트 식별 코드 (Business Key, 예: SITE_A)")
    @Column(name = "site_code", nullable = false, unique = true, length = 50)
    private String siteCode;

    @Comment("진입 도메인 패턴 (예: *.shop.com)")
    @Column(name = "domain_pattern", nullable = false, unique = true)
    private String domainPattern;

    @Comment("사이트 이름")
    @Column(nullable = false)
    private String name;

    @Comment("설명")
    private String description;

    @Comment("적용된 테마 폴더명 (기존 templates/themes/{이름} 매핑)")
    @Column(name = "theme_name", nullable = false, length = 50)
    private String themeName = "default";

    @Comment("사이트 운영 상태 (정상, 점검중, 정지 등)")
    @Column(nullable = false)
    @Setter
    private boolean maintenance = false;

    @Comment("읽기 전용 모드 여부")
    @Column(nullable = false)
    private boolean readOnly = false;

    // TODO: 사이트별 정책 (JSON) - 예: 비밀번호 복잡도, 가입 승인제 여부 등
    // @Column(columnDefinition = "json")
    // private String policy;


    @Builder
    public Tenant(String siteCode, String domainPattern, String name, String description, String themeName) {
        this.siteCode = siteCode;
        this.domainPattern = domainPattern;
        this.name = name;
        this.description = description;
        this.themeName = (themeName != null && !themeName.isBlank()) ? themeName : "default";
    }

    public void update(String name, String description, String themeName) {
        this.name = name;
        this.description = description;
        if (themeName != null && !themeName.isBlank()) {
            this.themeName = themeName;
        }
    }


}