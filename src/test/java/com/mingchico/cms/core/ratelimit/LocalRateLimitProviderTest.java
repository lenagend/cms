package com.mingchico.cms.core.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRateLimitProviderTest {

    // [수정 1] 테스트 클래스 멤버 변수로 선언 (Cannot resolve symbol 'provider' 해결)
    private LocalRateLimitProvider provider;

    @BeforeEach
    void setUp() {
        // 1. 비즈니스 설정 생성
        RateLimitProperties rateLimitProperties = new RateLimitProperties();
        rateLimitProperties.setCapacity(100);
        // [수정 2] setMode() 호출 삭제 (RateLimitProperties에서 mode 필드가 삭제됨)

        // 2. Provider 생성 (RateLimitProperties만 주입)
        provider = new LocalRateLimitProvider(rateLimitProperties);

        // 3. 초기화 (POJO로 테스트하므로 @PostConstruct가 자동 실행되지 않아 수동 호출)
        provider.init();
    }

    @Test
    @DisplayName("정상 동작: 전달된 용량만큼 요청이 허용되어야 한다")
    void allowRequestWithinCapacity() {
        // given
        int dynamicCapacity = 10; // 동적으로 10회 설정

        // when
        // setUp에서 초기화된 provider 사용
        ConsumptionProbe probe = provider.tryConsume("127.0.0.1", dynamicCapacity);

        // then
        assertThat(probe.isConsumed()).isTrue();
        assertThat(probe.getRemainingTokens()).isEqualTo(9);
    }

    @Test
    @DisplayName("차단 동작: 전달된 용량을 초과하면 요청이 거부되어야 한다")
    void rejectRequestExceedingCapacity() {
        // given
        int dynamicCapacity = 1; // 1회만 허용

        // when
        // 첫 번째 요청: 성공 (남은 토큰 0)
        provider.tryConsume("127.0.0.1", dynamicCapacity);

        // 두 번째 요청: 실패해야 함 (남은 토큰 0에서 소모 시도)
        ConsumptionProbe probe = provider.tryConsume("127.0.0.1", dynamicCapacity);

        // then
        assertThat(probe.isConsumed()).isFalse();
        assertThat(probe.getRemainingTokens()).isZero();
    }
}