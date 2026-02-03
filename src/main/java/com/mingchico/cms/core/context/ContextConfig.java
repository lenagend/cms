package com.mingchico.cms.core.context;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

@Configuration
@RequiredArgsConstructor
public class ContextConfig implements WebMvcConfigurer {

    private final MessageSource messageSource;
    private final ThymeleafViewResolver viewResolver;

    @PostConstruct
    public void init() {
        // 1. 정적 유틸리티 초기화
        MessageUtils.setMessageSource(messageSource);

        // 2. Thymeleaf 전역 변수 (#ctx) 등록
        if (viewResolver != null) {
            viewResolver.addStaticVariable("ctx", ContextHolder.class);
        }
    }
}