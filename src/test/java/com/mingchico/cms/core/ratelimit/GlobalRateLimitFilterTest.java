package com.mingchico.cms.core.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GlobalRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitProperties properties; // 설정을 조작하기 위해 주입

    @MockitoBean
    private RateLimitProvider rateLimitProvider;

    @Test
    @WithMockUser
    @DisplayName("테넌트별 동적 용량 적용: VIP 사이트는 설정된 높은 용량(1000)이 전달되어야 한다")
    void shouldUseCustomCapacityForVipTenant() throws Exception {
        // given
        // 1. 테스트용 프로퍼티 설정 (VIP 사이트는 1000회 허용)
        properties.getPerTenantCapacities().put("vip-site.com", 1000);

        ConsumptionProbe successProbe = mock(ConsumptionProbe.class);
        given(successProbe.isConsumed()).willReturn(true);
        given(successProbe.getRemainingTokens()).willReturn(999L);

        // Mocking: 어떤 인자가 들어오든 성공 리턴 (검증은 verify에서 함)
        given(rateLimitProvider.tryConsume(anyString(), anyInt())).willReturn(successProbe);

        // when
        mockMvc.perform(get("/api/test")
                        .header("X-Tenant-ID", "vip-site.com")) // VIP 테넌트로 요청
                .andExpect(status().isNotFound()) // 필터 통과 (404는 도메인 처리 결과일 뿐)
                .andExpect(header().string("X-Rate-Limit-Remaining", "999"));

        // then [핵심 검증]
        // Provider에게 전달된 capacity가 기본값(100)이 아니라 VIP값(1000)이어야 함
        verify(rateLimitProvider).tryConsume(anyString(), eq(1000));
    }

    @Test
    @WithMockUser
    @DisplayName("테넌트별 동적 용량 적용: 설정이 없는 일반 사이트는 기본 용량(100)이 전달되어야 한다")
    void shouldUseDefaultCapacityForNormalTenant() throws Exception {
        // given
        // 1. 기본 용량 확인 (설정 파일 기본값 or 100)
        int defaultCapacity = properties.getCapacity();

        ConsumptionProbe successProbe = mock(ConsumptionProbe.class);
        given(successProbe.isConsumed()).willReturn(true);
        given(successProbe.getRemainingTokens()).willReturn(99L);

        given(rateLimitProvider.tryConsume(anyString(), anyInt())).willReturn(successProbe);

        // when
        mockMvc.perform(get("/api/test")
                        .header("X-Tenant-ID", "normal-site.com")) // 설정에 없는 일반 사이트
                .andExpect(status().isNotFound());

        // then [핵심 검증]
        // Provider에게 전달된 capacity가 기본값이어야 함
        verify(rateLimitProvider).tryConsume(anyString(), eq(defaultCapacity));
    }
}