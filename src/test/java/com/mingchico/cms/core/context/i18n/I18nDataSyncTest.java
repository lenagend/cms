package com.mingchico.cms.core.context.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <h3>[초기 데이터 동기화 통합 테스트]</h3>
 * <p>
 * {@link MessageDataInitializer}가 구동될 때
 * 프로퍼티 파일의 내용이 DB로 안전하게 이관되는지 검증합니다.
 * </p>
 */
@SpringBootTest
@Transactional // 테스트 후 DB 롤백 보장
class I18nDataSyncTest {

    @Autowired MessageDataInitializer dataInitializer;
    @Autowired I18nMessageRepository messageRepository;

    @Test
    @DisplayName("Sync: DB에 없는 키는 파일 내용을 기준으로 새로 적재되어야 한다")
    void sync_NewMessages() {
        // "Sync 로직"을 검증하기 위해 기존 데이터를 모두 지워서 '초기 상태'로 만듭니다.
        messageRepository.deleteAll();

        long beforeCount = messageRepository.count();
        assertThat(beforeCount).isZero(); // 확실하게 0인지 확인

        // When
        // 앱 구동 시점의 run() 메서드 강제 호출
        dataInitializer.run(new DefaultApplicationArguments());

        // Then
        long afterCount = messageRepository.count();

        // 이제 0보다 커야 정상.
        assertThat(afterCount).isGreaterThan(0);

        // [검증] 실제 프로퍼티 파일의 내용(예: site.welcome)이 들어갔는지 확인
        // Initializer 코드의 TARGET_LOCALES에 Locale.ENGLISH("en")가 포함되어 있다고 가정
        boolean exists = messageRepository.existsByCodeAndLocale("site.welcome", "en");
        if (!exists) {
            // 만약 로직이 Locale.US("en-US")를 사용한다면 태그를 조정해야 함
            exists = messageRepository.existsByCodeAndLocale("site.welcome", "en-US");
        }
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Safety: 이미 DB에 존재하는 키는 파일 값으로 덮어쓰지 않아야 한다 (운영 데이터 보호)")
    void sync_SkipExisting() {
        // Given
        String code = "site.title";
        String locale = "en";
        
        // 운영자가 DB에서 직접 "My Custom CMS"로 수정했다고 가정
        I18nMessage existing = new I18nMessage(code, locale, "My Custom CMS");
        messageRepository.save(existing);

        // When
        // 초기화 로직 실행 (파일에는 "Original Title"이라고 적혀있을 것임)
        dataInitializer.run(new DefaultApplicationArguments());

        // Then
        I18nMessage result = messageRepository.findByCodeAndLocale(code, locale).orElseThrow();
        
        // [핵심] 파일 값("Original Title")이 아닌 DB 값("My Custom CMS")이 유지되어야 함
        assertThat(result.getMessage()).isEqualTo("My Custom CMS");
    }
}