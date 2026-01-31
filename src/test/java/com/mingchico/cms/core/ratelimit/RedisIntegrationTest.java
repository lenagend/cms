package com.mingchico.cms.core.ratelimit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class RedisIntegrationTest {

    @Autowired
    private RateLimitProvider rateLimitProvider;

    @Test
    @DisplayName("실제 Redis 연동: 토큰이 정상적으로 차감되는지 확인")
    //@Disabled("로컬 Redis 실행 시에만")
    void realRedisConsumptionTest() {
        // given
        String testIp = "192.168.0.100";
        int capacity = 100; // 테스트 용량

        // when
        // [수정] capacity 인자 전달
        var probe = rateLimitProvider.tryConsume(testIp, capacity);

        // then
        assertThat(probe.isConsumed()).isTrue();
    }
}