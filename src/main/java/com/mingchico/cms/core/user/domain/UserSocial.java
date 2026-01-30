package com.mingchico.cms.core.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_socials", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"provider", "providerId"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSocial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Comment("소셜 공급자")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Comment("공급자 측 식별값(sub)")
    @Column(nullable = false, length = 100)
    private String providerId;

    @Comment("소셜 이메일")
    private String email;

    @Comment("연동 일시")
    @Column(nullable = false)
    private LocalDateTime connectedAt;

    @Builder
    public UserSocial(User user, SocialProvider provider, String providerId, String email) {
        this.user = user;
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
        this.connectedAt = LocalDateTime.now();
    }
}