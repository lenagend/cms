package com.mingchico.cms.core.config;

import com.mingchico.cms.core.mdc.AsyncProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <h3>AsyncConfig 및 Virtual Thread 통합 테스트</h3>
 * <p>
 * 1. @Async가 가상 스레드(Virtual Thread)에서 실행되는가?
 * 2. 메인 스레드의 MDC 정보가 비동기 스레드로 잘 전파되는가?
 * </p>
 */
@Slf4j
@SpringBootTest(classes = AsyncConfigTest.TestConfig.class)
class AsyncConfigTest {

    @Autowired
    private TestAsyncService testAsyncService;

    @Test
    @DisplayName("가상 스레드 동작 및 MDC 전파 테스트")
    void testAsyncWithMdcAndVirtualThread() throws ExecutionException, InterruptedException {
        // 1. [Given] 메인 스레드에 MDC 정보 설정
        String traceId = "test-trace-id-999";
        MDC.put("correlationId", traceId);
        log.info("Main Thread: 요청 시작");

        // 2. [When] 비동기 메소드 호출
        // (비동기 결과를 확인하기 위해 CompletableFuture를 반환받음)
        CompletableFuture<AsyncResult> future = testAsyncService.runAsyncTask();

        // 3. [Then] 결과 검증
        AsyncResult result = future.get(); // 비동기 작업 끝날 때까지 대기

        log.info("Main Thread: 테스트 종료");

        // 검증 1: MDC가 전파되었는가?
        assertThat(result.mdcValue())
                .as("비동기 스레드 내부에서도 MDC 값이 유지되어야 합니다.")
                .isEqualTo(traceId);

        // 검증 2: 가상 스레드(Virtual Thread)인가?
        // (Java 21 환경에서만 통과)
        assertThat(result.isVirtual())
                .as("설정대로라면 가상 스레드(Virtual Thread)여야 합니다.")
                .isTrue();

        // 검증 3: 스레드 이름이 설정(yml/properties)대로 적용되었는가?
        // (TestConfig에서 'cms-vt-'로 설정함)
        assertThat(result.threadName())
                .as("스레드 이름 접두사가 설정 파일대로 적용되어야 합니다.")
                .startsWith("cms-vt-");
    }

    // --- 테스트를 위한 내부 설정 및 서비스 ---

    @Configuration
    @Import({AsyncConfig.class}) // 설정 로드
    @EnableConfigurationProperties(AsyncProperties.class) // 프로퍼티 클래스 활성화
    @EnableAsync
    static class TestConfig {
        @Bean
        public TestAsyncService testAsyncService() {
            return new TestAsyncService();
        }
    }

    @Service
    static class TestAsyncService {
        @Async
        public CompletableFuture<AsyncResult> runAsyncTask() {
            log.info("Async Thread: 작업 수행 중...");

            // 현재 스레드의 상태를 캡처해서 리턴
            return CompletableFuture.completedFuture(new AsyncResult(
                    MDC.get("correlationId"),
                    Thread.currentThread().isVirtual(),
                    Thread.currentThread().getName()
            ));
        }
    }

    // 결과 전달용 레코드 (Java 16+)
    record AsyncResult(String mdcValue, boolean isVirtual, String threadName) {}
}