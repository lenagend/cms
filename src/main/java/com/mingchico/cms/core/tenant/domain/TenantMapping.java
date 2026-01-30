package com.mingchico.cms.core.tenant.domain;

import com.mingchico.cms.core.common.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * <h3>[테넌트 매핑 엔티티]</h3>
 * <p>
 * 도메인 주소와 사이트 코드 간의 연결 정보를 저장합니다.
 * {@link BaseAuditEntity}를 상속받아 생성/수정 시간이 자동으로 관리됩니다.
 * </p>
 */
@Entity
@Table(name = "cms_tenant_mapping", indexes = {
        @Index(name = "idx_tenant_domain", columnList = "domain_pattern", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true) // 부모 클래스의 필드(시간), 작성자&수정자도 toString에 포함
public class TenantMapping extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 도메인 패턴
     * - 예: "customer-a.com", "*.myshop.com"
     */
    @Column(name = "domain_pattern", nullable = false, unique = true)
    private String domainPattern;

    /**
     * 연결될 사이트 코드
     * - 예: "SITE_A"
     */
    @Column(name = "site_code", nullable = false)
    private String siteCode;

    /**
     * 설명 (관리자용 메모)
     */
    @Column(name = "description")
    private String description;

    @Builder
    public TenantMapping(String domainPattern, String siteCode, String description) {
        this.domainPattern = domainPattern;
        this.siteCode = siteCode;
        this.description = description;
    }

    /**
     * [엔티티 수정 비즈니스 로직]
     * @param siteCode 변경할 사이트 코드
     * @param description 변경할 설명
     */
    public void update(String siteCode, String description) {
        this.siteCode = siteCode;
        this.description = description;
    }
}