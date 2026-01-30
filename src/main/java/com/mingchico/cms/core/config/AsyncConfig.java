package com.mingchico.cms.core.config;

import com.mingchico.cms.core.mdc.AsyncProperties;
import com.mingchico.cms.core.tenant.TenantContext;
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
@EnableAsync
@Configuration
@RequiredArgsConstructor // 생성자 주입을 통해 AsyncProperties를 가져옵니다.
public class AsyncConfig implements AsyncConfigurer {

    private final AsyncProperties asyncProperties;

    @Override
    public Executor getAsyncExecutor() {
        // 1. [스레드 팩토리 생성]
        // 가상 스레드를 생성하며, yml에서 설정한 이름("cms-async-")을 붙여줍니다.
        // Virtual Thread는 OS 스레드를 점유하지 않는 경량 스레드입니다.
        ThreadFactory virtualThreadFactory = Thread.ofVirtual()
                .name(asyncProperties.getThreadNamePrefix(), 0) // 이름 뒤에 0부터 증가하는 숫자 붙임
                .factory();

        // 2. [실행기 생성]
        // 기존의 ThreadPool(미리 만들어두고 재사용) 방식이 아니라,
        // 작업이 들어올 때마다 새로운 가상 스레드를 생성(New Thread Per Task)하는 방식을 사용합니다.
        Executor javaExecutor = Executors.newThreadPerTaskExecutor(virtualThreadFactory);

        // 3. [Spring 어댑터 및 데코레이터 적용]
        // Java의 기본 Executor를 스프링이 이해할 수 있는 형태로 감싸줍니다.
        TaskExecutorAdapter springExecutor = new TaskExecutorAdapter(javaExecutor);

        // [중요] 스레드가 바뀌어도 MDC(추적 ID)가 유지되도록 복사합니다.
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
            // 2. 사이트 코드 복사 (이게 없으면 비동기 작업에서 DB 조회를 못함)
            String currentSiteCode = TenantContext.getSiteCode();

            return () -> {
                try {
                    // [자식 가상 스레드]
                    // 1. 로그 문맥 복원
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    // 2. 사이트 코드 복원
                    if (currentSiteCode != null) {
                        TenantContext.setSiteCode(currentSiteCode);
                    }

                    runnable.run();
                } finally {
                    // 3. 정리 (MDC는 TenantContext 내부에서 일부 정리되지만 확실히 하기 위해)
                    TenantContext.clear(); // 내부에서 MDC.remove("siteCode") 도 수행됨
                    MDC.clear();
                }
            };
        }
    }
}