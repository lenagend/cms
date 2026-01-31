package com.mingchico.cms.core.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRateLimitProviderTest {

    @Test
    @DisplayName("정상 동작: 전달된 용량만큼 요청이 허용되어야 한다")
    void allowRequestWithinCapacity() {
        // given
        RateLimitProperties properties = new RateLimitProperties();
        properties.setMode(RateLimitProperties.Mode.LOCAL);
        // properties.setCapacity()는 이제 초기 로그용일 뿐, 실제 제한은 파라미터로 결정됨

        LocalRateLimitProvider provider = new LocalRateLimitProvider(properties);
        provider.init();

        int dynamicCapacity = 10; // 동적으로 10회 설정

        // when
        // [수정] capacity 인자(10) 전달
        ConsumptionProbe probe = provider.tryConsume("127.0.0.1", dynamicCapacity);

        // then
        assertThat(probe.isConsumed()).isTrue();
        assertThat(probe.getRemainingTokens()).isEqualTo(9);
    }

    @Test
    @DisplayName("차단 동작: 전달된 용량을 초과하면 요청이 거부되어야 한다")
    void rejectRequestExceedingCapacity() {
        // given
        RateLimitProperties properties = new RateLimitProperties();
        properties.setMode(RateLimitProperties.Mode.LOCAL);
        LocalRateLimitProvider provider = new LocalRateLimitProvider(properties);
        provider.init();

        int dynamicCapacity = 1; // 1회만 허용

        // when
        // 첫 번째 요청: 성공
        provider.tryConsume("127.0.0.1", dynamicCapacity);

        // 두 번째 요청: 실패해야 함
        ConsumptionProbe probe = provider.tryConsume("127.0.0.1", dynamicCapacity);

        // then
        assertThat(probe.isConsumed()).isFalse();
        assertThat(probe.getRemainingTokens()).isZero();
    }
}