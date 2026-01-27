package com.mingchico.cms.core.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cms.security.rate-limit")
public class RateLimitProperties {

    // 모드 선택: local (기본값) 또는 redis
    private boolean enabled;

    // IP당 1분 동안 허용할 요청 수
    private String mode;

    // 현재 구동 중인 서버 대수 (기본 1대) -> 로컬 모드일 때 1/n 계산용
    private int capacity;

    private int serverCount;

    // Redis 접속 정보 (Redis 모드일 때 사용)
    private String redisHost;
    private int redisPort;
}