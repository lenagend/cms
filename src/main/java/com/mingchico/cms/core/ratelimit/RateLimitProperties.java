package com.mingchico.cms.core.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <h3>[Rate Limit 설정 프로퍼티]</h3>
 * <p>
 * 애플리케이션의 처리율 제한(Rate Limit) 정책을 외부 설정 파일({@code application.yml} 등)로부터 읽어오는 클래스입니다.
 * {@code cms.security.rate-limit} 접두사를 사용하는 설정값들을 매핑합니다.
 * </p>
 *
 * <h3>주요 특징</h3>
 * <ul>
 * <li><b>동적 활성화:</b> {@code enabled} 설정을 통해 전체 Rate Limit 필터의 작동 여부를 즉각적으로 제어합니다.</li>
 * <li><b>유연한 모드 전환:</b> 단일 서버 환경({@code LOCAL})과 분산 서버 환경({@code REDIS}) 중 아키텍처에 맞는 모드 선택이 가능합니다.</li>
 * <li><b>데이터 검증:</b> Jakarta Validation({@code @Min}, {@code @NotNull} 등)을 통해 허용량(capacity)이 1 이상인지 검증하여
 * 애플리케이션 실행 시점의 안정성을 확보합니다.</li>
 * </ul>
 *
 * @see <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config">Spring Boot External Configuration</a>
 */
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
     * 기본허용량 (Capacity)
     * -별도 설정이 없는 테넌트는 이 값을 따릅니다.
     * - IP당 1분 동안 허용할 최대 요청 수
     * - 예: 100이면 1분에 100회 요청 가능 (약 0.6초당 1회)
     */
     @Min(1)
     private int capacity = 100;

    // [추가됨: 테넌트별 개별 용량 설정]
    // Key: siteCode, Value: capacity
    // 예: "vip-shop": 1000, "bad-shop": 10
    private Map<String, Integer> perTenantCapacities = new HashMap<>();

    /**
     * [신뢰할 수 있는 프록시 IP 목록]
     * 이 IP 대역(CIDR)에서 온 요청일 경우에만 X-Forwarded-For 헤더를 신뢰합니다.
     * 비어있을 경우, 보안을 위해 헤더를 무시하고 RemoteAddr만 사용합니다.
     * 예: ["127.0.0.1", "10.0.0.0/8", "192.168.0.0/16"] (내부망, 로드밸런서 IP 등)
     */
    private List<String> trustedProxies = List.of("127.0.0.1", "0:0:0:0:0:0:0:1");

    /**
     * [Rate Limit 제외 확장자 목록]
     * 정적 리소스 등 카운팅에서 제외할 파일 확장자들을 설정 파일에서 관리합니다.
     */
    private Set<String> excludedExtensions = Set.of(
            ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".woff", ".woff2", ".ttf", ".webp"
    );

    private Set<String> excludedPaths = Set.of(
            "/actuator",
            "/health",
            "/metrics"
    );
}
