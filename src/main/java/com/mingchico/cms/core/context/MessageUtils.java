package com.mingchico.cms.core.context;

import lombok.Setter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * <h3>[Message Utility]</h3>
 * <p>
 * 정적 컨텍스트에서 다국어 메시지를 쉽게 가져오기 위한 헬퍼입니다.
 * </p>
 */
@Slf4j
@UtilityClass
public class MessageUtils {

    @Setter
    private static MessageSource messageSource;

    public static String get(String code, Object... args) {
        if (messageSource == null) {
            // 초기화 누락 시 경고 로그
            log.warn("MessageUtils used before initialization! code={}", code);
            return code;
        }
        try {
            return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            return code;
        }
    }
}