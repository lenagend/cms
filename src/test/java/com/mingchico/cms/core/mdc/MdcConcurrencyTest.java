package com.mingchico.cms.core.mdc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MdcConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MdcLoggingFilter mdcLoggingFilter;

    @PostConstruct
    void setup() {
        // [핵심] MockMvc에 필터를 직접 등록해줍니다.
        // 이렇게 해야 테스트 시에도 필터 로직(ID 생성 및 헤더 주입)이 실행됩니다.
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new CharacterEncodingFilter("UTF-8", true), mdcLoggingFilter)
                .build();
    }

    @Test
    @DisplayName("동시 요청 시 모든 사용자는 서로 고유한 추적 ID를 가져야 한다")
    void multipleRequestsShouldHaveUniqueCorrelationIds() throws InterruptedException {
        int numberOfRequests = 100;

        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch latch = new CountDownLatch(numberOfRequests);
            Set<String> generatedIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

            for (int i = 0; i < numberOfRequests; i++) {
                executorService.submit(() -> {
                    try {
                        // 실제 존재하는 엔드포인트가 없다면 404가 나더라도 헤더는 찍혀야 합니다.
                        MvcResult result = mockMvc.perform(get("/api/health-check"))
                                .andReturn();

                        String correlationId = result.getResponse().getHeader("X-Correlation-ID");
                        if (correlationId != null) {
                            generatedIds.add(correlationId);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();

            // 검증: 0이 아니라 100이 나와야 성공입니다.
            assertThat(generatedIds)
                    .as("MDC 필터가 작동하여 모든 요청에 고유 ID를 부여해야 합니다.")
                    .hasSize(numberOfRequests);
        }
    }
}