package com.mingchico.cms.core.context.i18n;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * <h3>[DatabaseMessageSource 단위 테스트]</h3>
 * <p>
 * 캐시(Cache)와 DB(Repository) 사이의 <b>Look-aside 전략</b>과
 * <b>Sentinel Value(NULL 마커)</b> 처리 로직을 중점적으로 검증합니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class DatabaseMessageSourceTest {

    @Mock I18nMessageRepository messageRepository;
    @Mock CacheManager cacheManager;
    @Mock Cache messageCache;

    DatabaseMessageSource messageSource;

    @BeforeEach
    void setUp() {
        // [사전 조건] 생성자에서 Cache 존재 여부를 체크하므로 Mock을 주입해줍니다.
        given(cacheManager.getCache("i18n_messages")).willReturn(messageCache);
        messageSource = new DatabaseMessageSource(messageRepository, cacheManager);
    }

    @Test
    @DisplayName("Scenario 1: 캐시에 값이 있다면 DB 조회 없이 즉시 반환한다 (Cache Hit)")
    void resolveCode_CacheHit() {
        // Given
        String code = "welcome.msg";
        Locale locale = Locale.KOREA;
        String cacheKey = "welcome.msg|ko-KR";

        // 캐시가 이미 값을 가지고 있다고 가정
        given(messageCache.get(eq(cacheKey), any(Callable.class))).willReturn("환영합니다");

        // When
        MessageFormat result = messageSource.resolveCode(code, locale);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.toPattern()).isEqualTo("환영합니다");

        // [검증] DB는 호출되지 않아야 함 (Lazy Loading)
        verify(messageRepository, never()).findByCodeAndLocale(any(), any());
    }

    @Test
    @DisplayName("Scenario 2: 캐시가 비어있다면 DB를 조회하고 결과를 반환한다 (Cache Miss -> DB Hit)")
    void resolveCode_CacheMiss_DbHit() throws Exception {
        // Given
        String code = "login.fail";

        // Mockito Stubbing에서 "en-US"를 기대하고 있으므로 이를 맞춰줍니다.
        Locale locale = Locale.US;

        // 캐시 조회 람다 실행 설정
        given(messageCache.get(anyString(), (Callable<String>) any(Callable.class))).willAnswer(invocation -> {
            Callable<String> loader = invocation.getArgument(1);
            return loader.call();
        });

        // DB Mock 설정 (여기서 "en-US"를 기대하고 있음)
        I18nMessage dbEntity = new I18nMessage(code, locale, "Login Failed");

        // [일치 확인] 위에서 Locale.US를 썼으므로 이제 "en-US"가 정확히 매칭됨
        given(messageRepository.findByCodeAndLocale(code, "en-US"))
                .willReturn(Optional.of(dbEntity));

        // When
        MessageFormat result = messageSource.resolveCode(code, locale);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.toPattern()).isEqualTo("Login Failed");
    }

    @Test
    @DisplayName("Scenario 3: DB에도 데이터가 없다면 NULL을 반환하여 부모(File) 소스로 위임한다")
    void resolveCode_DbMiss_Fallback() throws Exception {
        // Given
        String code = "unknown.code";
        Locale locale = Locale.KOREA;

        // [수정 포인트] thenAnswer -> willAnswer
        given(messageCache.get(anyString(), any(Callable.class))).willAnswer(invocation -> {
            Callable<?> loader = invocation.getArgument(1);
            return loader.call();
        });

        given(messageRepository.findByCodeAndLocale(code, "ko-KR"))
                .willReturn(Optional.empty());

        // When
        MessageFormat result = messageSource.resolveCode(code, locale);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Scenario 4: Sentinel Value(@@I18N_NULL@@)가 캐싱되어 있다면 즉시 null을 반환한다")
    void resolveCode_SentinelValue() {
        // Given
        // "DB에 없음" 상태가 이미 캐싱된 경우 (Cache Penetration 방지)
        given(messageCache.get(anyString(), any(Callable.class))).willReturn("@@I18N_NULL@@");

        // When
        MessageFormat result = messageSource.resolveCode("some.code", Locale.KOREAN);

        // Then
        assertThat(result).isNull();
        // DB 조회 시도조차 하지 않아야 함
        verify(messageRepository, never()).findByCodeAndLocale(any(), any());
    }
}