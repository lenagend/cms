package com.mingchico.cms.core.context.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class I18nConfig {

    private final I18nMessageRepository messageRepository;
    private final CacheManager cacheManager;

    @Bean
    public MessageSource messageSource() {
        // 1. File Message Source (Parent: 안전장치)
        ReloadableResourceBundleMessageSource fileSource = new ReloadableResourceBundleMessageSource();
        fileSource.setBasename("classpath:messages");
        fileSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        fileSource.setCacheSeconds(60);
        fileSource.setUseCodeAsDefaultMessage(true);

        // 2. DB Message Source (Child: 우선순위 높음)
        DatabaseMessageSource dbSource = new DatabaseMessageSource(messageRepository, cacheManager);
        dbSource.setParentMessageSource(fileSource);

        return dbSource;
    }
}