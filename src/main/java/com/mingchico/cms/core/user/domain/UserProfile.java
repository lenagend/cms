package com.mingchico.cms.core.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // --- [Standard Fields] ---

    @Comment("실명")
    @Column(nullable = false, length = 50)
    private String realName;

    @Comment("닉네임")
    @Column(length = 50)
    private String nickname;

    @Comment("휴대폰 번호")
    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @Comment("이메일")
    @Column(nullable = false, length = 100)
    private String email;

    @Comment("생년월일")
    private LocalDate birthDate;

    @Comment("성별")
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    // --- [Extension Field: Dynamic Attributes] ---

    @Comment("동적 속성(JSON)")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> attributes = new HashMap<>();

    @Builder
    public UserProfile(String realName, String nickname, String phoneNumber, String email) {
        this.realName = realName;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.email = email;
    }

    public void assignUser(User user) {
        this.user = user;
    }

    // --- [Attributes Logic] ---

    /**
     * 속성 값 설정
     */
    public void setAttribute(String key, Object value) {
        if (value == null) {
            this.attributes.remove(key);
        } else {
            this.attributes.put(key, value);
        }
    }

    /**
     * 속성 값 조회 (Type Safe 유틸리티 메서드 필요 시 추가)
     */
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    /**
     * [핵심] 필수 속성 검증
     * CMS 설정(UserAttributeDefinition)에서 정의된 필수 키 목록을 받아,
     * 현재 프로필에 해당 값이 있는지 검사합니다.
     * * @param requiredKeys 필수 속성 키 목록 (예: ["department", "agreed_marketing"])
     * @throws IllegalArgumentException 필수 값이 누락된 경우
     */
    public void validateRequiredAttributes(List<String> requiredKeys) {
        for (String key : requiredKeys) {
            if (!attributes.containsKey(key) || attributes.get(key) == null) {
                throw new IllegalArgumentException("필수 속성이 누락되었습니다: " + key);
            }
        }
    }
}