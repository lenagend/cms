package com.mingchico.cms.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h3>[CMS 초기화 설정 프로퍼티]</h3>
 * <p>
 * {@code application.yml}의 `cms.bootstrap` 하위 설정을 객체로 매핑합니다.
 * <br>
 * <b>장점:</b>
 * <ul>
 * <li>Type-Safe: 컴파일 시점에 타입 체크가 가능합니다.</li>
 * <li>Autocomplete: IDE에서 설정 키 자동 완성을 지원합니다.</li>
 * <li>Grouping: 연관된 설정을 계층적으로 관리할 수 있습니다.</li>
 * </ul>
 * </p>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cms.bootstrap")
public class BootstrapProperties {

    /** 관리자 계정 설정 그룹 */
    private AdminUser admin = new AdminUser();

    /** 관리자 전용 테넌트 설정 그룹 */
    private SystemTenant tenant = new SystemTenant();

    @Getter
    @Setter
    public static class AdminUser {
        private String email;
        private String password;
        private String nickname;
    }

    @Getter
    @Setter
    public static class SystemTenant {
        /**
         * 관리자 사이트 식별 코드 (예: MING_CONSOLE, BACK_OFFICE 등)
         * <p>보안을 위해 추측하기 어려운 값을 권장합니다.</p>
         */
        private String siteCode;

        /**
         * 관리자 접속 도메인 패턴 (예: console.mingchico.com)
         */
        private String domain;

        /** 관리자 사이트 이름 */
        private String name;
    }
}