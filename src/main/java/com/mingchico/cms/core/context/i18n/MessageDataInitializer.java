package com.mingchico.cms.core.context.i18n;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * <h3>[초기 데이터 동기화]</h3>
 * <p>
 * 배포 시 'messages_xx.properties' 파일의 내용을 DB로 적재합니다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageDataInitializer implements ApplicationRunner {

    private final I18nMessageRepository repository;

    // 동기화 대상 언어 목록 (확장 시 여기에 추가)
    private static final List<Locale> TARGET_LOCALES = List.of(
            Locale.KOREAN,
            Locale.ENGLISH,
            Locale.JAPANESE,
            Locale.CHINESE
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("🚀 Starting I18n Message Sync (File -> DB)...");
        int totalAdded = 0;

        for (Locale locale : TARGET_LOCALES) {
            try {
                // messages_ko.properties, messages_en.properties 등을 로드
                ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
                Enumeration<String> keys = bundle.getKeys();

                while (keys.hasMoreElements()) {
                    String code = keys.nextElement();
                    String message = bundle.getString(code);

                    // [Safe Insert] 운영 중 관리자가 수정한 내용을 덮어쓰지 않기 위해
                    // DB에 데이터가 '없는 경우에만' 파일을 기준으로 추가합니다.
                    if (!repository.existsByCodeAndLocale(code, locale.toLanguageTag())) {
                        repository.save(new I18nMessage(code, locale, message));
                        totalAdded++;
                    }
                }
            } catch (MissingResourceException e) {
                // 해당 언어의 프로퍼티 파일이 없으면 조용히 스킵 (Optional)
                log.debug("ℹ️ No properties file found for locale: {}", locale);
            }
        }

        if (totalAdded > 0) {
            log.info("✅ Synced {} new messages to DB from properties files.", totalAdded);
        } else {
            log.info("👌 DB is up-to-date. No new messages synced.");
        }
    }
}