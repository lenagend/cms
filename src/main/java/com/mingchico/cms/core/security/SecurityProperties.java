package com.mingchico.cms.core.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * <h3>[보안 설정 프로퍼티]</h3>
 * <p>
 * {@code application.yml}의 'cms.security' 하위 설정을 매핑합니다.
 * <br>
 * <b>개선점:</b> @Validated를 통해 애플리케이션 구동 시점에 설정 오류(키 누락 등)를 잡아냅니다.
 * </p>
 */
@Validated
@ConfigurationProperties(prefix = "cms.security")
public record SecurityProperties(
        @Valid RememberMe rememberMe,
        @Valid SessionControl session
) {

    public record RememberMe(
            @NotBlank(message = "Remember-Me 암호화 키는 필수입니다.")
            String key,

            @Min(value = 60, message = "Remember-Me 유효 시간은 최소 60초 이상이어야 합니다.")
            int validitySeconds
    ) {}

    public record SessionControl(
            @NotNull Map<String, Integer> roleLimits,   // Role 이름별 제한 (e.g., ADMIN: -1)
            @NotNull Map<String, Integer> siteLimits,   // Site Code별 제한 (e.g., ENTERPRISE: 10)
            @Min(1) int defaultLimit                    // 기본값
    ) {}
}