package com.mingchico.cms.core.user.domain;

import com.mingchico.cms.core.common.BaseAuditEntity;
import com.mingchico.cms.core.tenant.domain.Tenant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * <h3>[사이트 멤버십 (Membership)]</h3>
 * <p>
 * User와 Tenant 간의 N:M 관계를 해소하는 연결 엔티티입니다.
 * <b>개선점:</b> String siteCode 대신 Tenant 엔티티를 직접 참조하여 DB 무결성(FK)을 보장합니다.
 * </p>
 */
@Entity
@Table(name = "memberships", uniqueConstraints = {
        // 한 유저는 한 테넌트에 대해 하나의 멤버십만 가짐
        @UniqueConstraint(name = "uk_membership_user_tenant", columnNames = {"user_id", "tenant_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Comment("소속 테넌트 (FK)")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Comment("해당 사이트 내에서의 권한")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Comment("사이트별 활동 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status;

    @Builder
    public Membership(User user, Tenant tenant, Role role, MembershipStatus status) {
        this.user = user;
        this.tenant = tenant;
        this.role = role != null ? role : Role.USER;
        this.status = status != null ? status : MembershipStatus.ACTIVE;
    }

    public enum MembershipStatus {
        PENDING, ACTIVE, BANNED, WITHDRAWN
    }
}