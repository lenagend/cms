package com.mingchico.cms.core.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

/**
 * [Rate Limit 설정 프로퍼티]
 * 애플리케이션의 처리율 제한(Rate Limit) 정책을 외부 설정 파일(application.yml 등)로부터 읽어오는 클래스입니다.
 *
 * 주요 특징:
 * - 동적 활성화: cms.security.rate-limit.enabled 설정으로 필터 작동 여부를 제어합니다.
 * - 유연한 모드 전환: 단일 서버 환경(LOCAL)과 분산 서버 환경(REDIS) 중 선택이 가능합니다.
 * - 데이터 검증: Jakarta Validation을 통해 허용량(capacity)이 1 이상인지 검증하여 안정성을 확보합니다.
 */
@Getter
@Setter
// ... (이하 코드 동일)
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cms.security.rate-limit")
@Validated
public class RateLimitProperties {

    /**
     * Rate Limit 기능 활성화 여부 (기본값: true)
     * - false로 설정 시 필터가 등록되지 않아 성능 오버헤드가 완전히 제거됨.
     */
    private boolean enabled = true;

    /**
     * 모드 선택
     * - "LOCAL": 각 서버 메모리에서 관리 (간단함, 성능 빠름, 서버 간 공유 안 됨)
     * - "REDIS": Redis를 통해 관리 (분산 환경에서 정확한 제어 가능, 네트워크 비용 발생)
     */
     public enum Mode { LOCAL, REDIS }
     private Mode mode = Mode.LOCAL;

    /**
     * 허용량 (Capacity)
     * - IP당 1분 동안 허용할 최대 요청 수
     * - 예: 100이면 1분에 100회 요청 가능 (약 0.6초당 1회)
     */
     @Min(1)
     private int capacity = 100;
}
