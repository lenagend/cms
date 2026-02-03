package com.mingchico.cms.core.context.i18n;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.support.AbstractMessageSource;
import org.springframework.lang.NonNull;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * <h3>[DB 기반 메시지 소스]</h3>
 * <p>
 * 1순위로 DB(캐시)를 조회하고, 없으면 부모 소스(파일)로 위임하는 하이브리드 전략을 구현합니다.
 * <br>
 * <b>[Sentinel Value 패턴]</b><br>
 * 캐시 직렬화 호환성을 위해 Optional 대신 특수 문자열("@@I18N_NULL@@")을 사용하여
 * '데이터 없음(Cache Miss)' 상태를 캐싱합니다.
 * </p>
 */
@Slf4j
public class DatabaseMessageSource extends AbstractMessageSource {

    private static final String CACHE_NAME = "i18n_messages";

    // 실제 메시지와 충돌할 확률이 '0'에 수렴하는 특수 마커
    private static final String NULL_PLACEHOLDER = "@@I18N_NULL@@";

    // 코드에 ':'나 '.'이 들어가도 안전하도록 구분자를 '|'로
    private static final String KEY_SEPARATOR = "|";

    private final I18nMessageRepository messageRepository;
    private final Cache messageCache;

    public DatabaseMessageSource(I18nMessageRepository messageRepository, CacheManager cacheManager) {
        this.messageRepository = messageRepository;

        // [Fail-Fast] 캐시 설정 누락은 런타임에 치명적이므로 기동 시점에 차단
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            throw new IllegalStateException("❌ Cache Config Error: '" + CACHE_NAME + "' cache is not found.");
        }
        this.messageCache = cache;
    }

    /**
     * <h3>[메시지 코드 해소]</h3>
     * DB(캐시)에서 메시지를 조회합니다. 결과가 없거나 마커(NULL)인 경우 null을 반환하여 부모 소스로 위임합니다.
     */
    @Override
    protected MessageFormat resolveCode(@NonNull String code, @NonNull Locale locale) {
        // [Cache Key] 키 충돌 방지를 위해 구분자(|) 사용 (ex: "greeting|ko-KR")
        String key = code + KEY_SEPARATOR + locale.toLanguageTag();

        /*
         * [Look Aside 전략]
         * 1. 캐시 조회 (Hit) -> 즉시 반환
         * 2. 캐시 없음 (Miss) -> DB 조회 -> 캐시 적재 (없으면 NULL_PLACEHOLDER 캐싱)
         */
        String cached = messageCache.get(key, () ->
                messageRepository.findByCodeAndLocale(code, locale.toLanguageTag())
                        .map(I18nMessage::getMessage)
                        .orElse(NULL_PLACEHOLDER)
        );

        /*
         * [Fallback]
         * DB에도 값이 없다면(NULL_PLACEHOLDER), null을 반환하여
         * Spring이 다음 단계인 'File MessageSource'를 찾도록 유도합니다.
         */
        if (cached == null || NULL_PLACEHOLDER.equals(cached)) {
            return null;
        }

        // 유효한 메시지인 경우 포맷터 반환
        return new MessageFormat(cached, locale);
    }

    /**
     * 관리자 화면에서 메시지 수정 시 호출하여 캐시를 갱신합니다.
     */
    public void clearCache(String code, Locale locale) {
        // [개선 2 반영] 키 생성 규칙 통일
        String key = code + KEY_SEPARATOR + locale.toLanguageTag();

        // [개선 3] 생성자에서 null 체크를 완료했으므로 불필요한 if문 제거
        messageCache.evict(key);
        log.info("♻️ I18n Cache Evicted: {}", key);
    }
}