package com.mingchico.cms.core.context.i18n;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.util.Locale;

/**
 * <h3>[다국어 메시지 엔티티]</h3>
 * <p>
 * 코드(Code)와 로케일(Locale)의 조합으로 유니크한 메시지를 관리합니다.
 * </p>
 */
@Entity
@Table(name = "i18n_messages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_code_locale", columnNames = {"code", "locale"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class I18nMessage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 20) // toLanguageTag() 대응을 위해 길이 여유
    private String locale;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    // 생성자에서 Locale 타입 강제
    public I18nMessage(String code, Locale locale, String message) {
        Assert.notNull(locale, "Locale must not be null");
        this.code = code;
        this.locale = locale.toLanguageTag(); // "ko-KR" 표준 태그 사용
        this.message = message;
    }

    // 문자열로 들어올 경우를 대비한 편의 생성자 (검증 로직 포함)
    public I18nMessage(String code, String localeStr, String message) {
        this(code, Locale.forLanguageTag(localeStr), message);
    }

    public void updateMessage(String newMessage) {
        this.message = newMessage;
    }
}