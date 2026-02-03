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
        // [Static Injection]
        // 스프링 빈이 아닌 일반 유틸리티 클래스나 POJO에서도
        // MessageSource 기능을 사용할 수 있도록 정적 필드에 주입합니다.
        MessageUtils.setMessageSource(messageSource);

        // [Global View Variables]
        // Thymeleaf 템플릿 어디서든 {@code ${@ctx.getUser()}} 형태로 접근 가능하도록 설정
        if (viewResolver != null) {
            viewResolver.addStaticVariable("ctx", ContextHolder.class);
        }
    }
}