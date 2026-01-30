package com.mingchico.cms.core.user.domain;

import com.mingchico.cms.core.common.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <h3>[전역 사용자 (Global User)]</h3>
 * <p>
 * 시스템 전체에서 유일한 사용자 신원(Identity)입니다.
 * <b>개선점:</b> 불필요한 username을 제거하고 email을 고유 식별자로 사용합니다.
 * </p>
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("이메일 (로그인 ID 겸용, 전역 유니크)")
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Comment("표시용 이름 (닉네임, 중복 허용)")
    @Column(nullable = false, length = 50)
    private String nickname;

    @Comment("암호화된 비밀번호")
    @Column(nullable = false, length = 100)
    private String password;

    @Comment("전역 계정 상태 (시스템 전체 차단 여부)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    // --- [Security Audit] ---
    private LocalDateTime lastLoginAt;
    private int loginFailCount;

    // --- [Relations] ---

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private UserProfile profile;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserSocial> socialAccounts = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserConsent> consents = new ArrayList<>();

    // [핵심] 사이트별 멤버십
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Membership> memberships = new ArrayList<>();

    @Builder
    public User(String email, String nickname, String password, UserStatus status) {
        this.email = email;
        this.nickname = nickname;
        this.password = password;
        this.status = status != null ? status : UserStatus.ACTIVE;
    }

    // 비즈니스 메서드
    public void recordLoginSuccess() {
        this.lastLoginAt = LocalDateTime.now();
        this.loginFailCount = 0;
    }
}