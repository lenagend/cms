package com.mingchico.cms.core.ratelimit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Redis 통합 테스트]
 * 실제 로컬 Redis가 떠있어야 성공합니다.
 * CI/CD 환경에서는 Testcontainers를 쓰거나 이 테스트를 제외(@Disabled)해야 합니다.
 */
@SpringBootTest
// application.yml의 설정을 오버라이드하여 REDIS 모드로 강제
@ActiveProfiles("test") // 필요시 application-test.yml 생성
public class RedisIntegrationTest {

    @Autowired
    private RateLimitProvider rateLimitProvider;

    @Test
    @DisplayName("실제 Redis 연동: 토큰이 정상적으로 차감되는지 확인")
    // 로컬 Redis가 켜져있지 않으면 실패하므로 평소엔 비활성화
    @Disabled("로컬 Redis 실행 시에만 주석 해제 후 테스트하세요") 
    void realRedisConsumptionTest() {
        // given
        String testIp = "192.168.0.100";

        // when
        var probe = rateLimitProvider.tryConsume(testIp);

        // then
        // 실제 Redis에 키가 생성되고 토큰이 줄어들었는지 확인
        assertThat(probe.isConsumed()).isTrue();
    }
}
