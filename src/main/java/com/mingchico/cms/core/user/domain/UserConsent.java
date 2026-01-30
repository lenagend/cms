package com.mingchico.cms.core.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * <h3>[Consent] 약관 동의 내역</h3>
 * <p>
 * 서비스 이용약관, 개인정보 처리방침, 마케팅 수신 동의 등
 * 법적 효력이 필요한 동의 내역을 관리합니다.
 * </p>
 */
@Entity
@Table(name = "user_consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String termCode;

    @Column(nullable = false)
    private boolean agreed;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime agreedAt;

    // IP 주소 등을 남겨두면 분쟁 시 증거 자료로 활용 가능
    @Column(length = 45)
    private String ipAddress;

    public UserConsent(User user, String termCode, boolean agreed, String ipAddress) {
        this.user = user;
        this.termCode = termCode;
        this.agreed = agreed;
        this.ipAddress = ipAddress;
    }
}