package com.mingchico.cms.core.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * <h3>[사용자 속성 정의]</h3>
 * <p>
 * UserProfile의 JSON 필드에 저장될 데이터의 '스펙'을 정의합니다.
 * 관리자 페이지에서 이 데이터를 추가하면, 회원가입 폼이나 마이페이지에 자동으로 입력 필드가 생성되는 구조입니다.
 * </p>
 */
@Entity
@Table(name = "user_attribute_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAttributeDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("속성 키 (JSON key)")
    @Column(nullable = false, unique = true, length = 50)
    private String attributeKey; // 예: "job_title", "mbti"

    @Comment("속성 표시명 (Label)")
    @Column(nullable = false, length = 100)
    private String label; // 예: "직급", "MBTI"

    @Comment("데이터 타입 (TEXT, NUMBER, DATE, SELECT 등)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttributeType inputType;

    @Comment("필수 입력 여부")
    @Column(nullable = false)
    private boolean required;

    @Comment("활성화 여부")
    @Column(nullable = false)
    private boolean active;
    
    @Comment("정렬 순서")
    private int displayOrder;

    public UserAttributeDefinition(String attributeKey, String label, AttributeType inputType, boolean required) {
        this.attributeKey = attributeKey;
        this.label = label;
        this.inputType = inputType;
        this.required = required;
        this.active = true;
    }
}