package com.mingchico.cms.core.config;

import com.mingchico.cms.core.mdc.AsyncProperties;
import com.mingchico.cms.core.tenant.TenantContext;
import com.mingchico.cms.core.tenant.dto.TenantInfo; // [Import 추가]
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * <h3>비동기 설정 (Java 21 Virtual Threads 적용)</h3>
 * <p>
 * {@code @Async}가 붙은 작업을 처리할 실행기(Executor)를 정의합니다.
 * </p>
 */
//  proxyTargetClass = true 옵션 추가
// -> 인터페이스(TenantResolver)가 있어도 JDK Proxy가 아닌 CGLIB(클래스 기반) 프록시를 강제합니다.
// -> 이를 통해 handleTenantRouteChanged, refreshRules 같은 구현체의 메서드도 AOP 적용 대상이 됩니다.
@EnableAsync(proxyTargetClass = true)
@Configuration
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final AsyncProperties asyncProperties;

    @Override
    public Executor getAsyncExecutor() {
        // 1. [스레드 팩토리 생성]
        ThreadFactory virtualThreadFactory = Thread.ofVirtual()
                .name(asyncProperties.getThreadNamePrefix(), 0)
                .factory();

        // 2. [실행기 생성] (New Thread Per Task)
        Executor javaExecutor = Executors.newThreadPerTaskExecutor(virtualThreadFactory);

        // 3. [Spring 어댑터 및 데코레이터 적용]
        TaskExecutorAdapter springExecutor = new TaskExecutorAdapter(javaExecutor);
        springExecutor.setTaskDecorator(new MdcTaskDecorator());

        return springExecutor;
    }

    /**
     * MDC(로깅) + TenantContext(사이트정보) 전파자
     */
    static class MdcTaskDecorator implements TaskDecorator {
        @Override
        @NonNull
        public Runnable decorate(@NonNull Runnable runnable) {
            // [부모 스레드]
            // 1. 로그 문맥 복사
            Map<String, String> contextMap = MDC.getCopyOfContextMap();

            // 2. [수정됨] TenantInfo 객체 통째로 복사
            // (단순 String siteCode가 아니라 전체 메타데이터를 넘겨야 함)
            TenantInfo tenantInfo = TenantContext.getTenant();

            return () -> {
                try {
                    // [자식 가상 스레드]
                    // 1. 로그 문맥 복원
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }

                    // 2. [수정됨] TenantInfo 복원
                    // setSiteCode() 대신 setContext()를 사용해야 합니다.
                    if (tenantInfo != null) {
                        TenantContext.setContext(tenantInfo);
                    }

                    runnable.run();
                } finally {
                    // 3. 정리
                    TenantContext.clear();
                    MDC.clear();
                }
            };
        }
    }
}