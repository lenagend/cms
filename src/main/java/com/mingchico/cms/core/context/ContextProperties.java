package com.mingchico.cms.core.context;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * <h3>[Context 모듈 설정]</h3>
 * <p>
 * yml에서 'cms.context'로 시작하는 설정을 관리합니다.
 * 운영 환경에서 튜닝 가능하도록 설계되었습니다.
 * </p>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cms.context")
public class ContextProperties {

    private Channel channel = new Channel();

    @Getter
    @Setter
    public static class Channel {
        /** 관리자 API 경로 프리픽스 (기본: /api/admin) */
        private String adminApiPrefix = "/api/admin";
        /** 일반 API 경로 프리픽스 (기본: /api) */
        private String apiPrefix = "/api";
        /** 관리자 웹 경로 프리픽스 (기본: /admin) */
        private String adminPrefix = "/admin";
    }
}