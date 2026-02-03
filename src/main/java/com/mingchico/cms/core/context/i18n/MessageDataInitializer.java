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
 * 이미 DB에 존재하는 키는 건드리지 않아(Skip), 운영자가 수정한 내용을 보존합니다.
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

                    // [중복 방지] DB에 없을 때만 Insert (운영 데이터 보호)
                    if (!repository.existsByCodeAndLocale(code, locale.toLanguageTag())) {
                        repository.save(new I18nMessage(code, locale, message));
                        totalAdded++;
                    }
                }
            } catch (MissingResourceException e) {
                // 특정 언어 파일이 아직 없어도 에러 없이 넘어가도록 처리
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