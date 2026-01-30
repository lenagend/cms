package com.mingchico.cms.core.mdc;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h3>비동기 처리 설정 프로퍼티 (Async Properties)</h3>
 * <p>
 * 비동기 작업({@code @Async})과 관련된 설정을 외부 파일(yml)에서 관리하는 클래스입니다.
 * </p>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cms.async")
public class AsyncProperties {

    /**
     * 비동기 스레드 이름 접두사
     * - 로그에서 어떤 스레드가 작업을 처리했는지 식별하기 위함입니다.
     * - 예: "cms-async-" -> 로그에 "cms-async-0", "cms-async-1" 등으로 찍힘
     */
    private String threadNamePrefix = "cms-async-";
}