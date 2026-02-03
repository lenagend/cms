package com.mingchico.cms.core.ratelimit;

import com.mingchico.cms.core.tenant.domain.Tenant;
import com.mingchico.cms.core.tenant.repository.TenantRepository;
import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith; // [필수] startsWith 추가
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GlobalRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private TenantRepository tenantRepository;

    @MockitoBean
    private RateLimitProvider rateLimitProvider;

    @BeforeEach
    void setUp() {
        // 테스트 통과를 위한 가짜 테넌트 데이터 주입
        saveTenant("vip-site.com", "vip.com");
        saveTenant("normal-site.com", "normal.com");
    }

    private void saveTenant(String siteCode, String domain) {
        if (!tenantRepository.existsBySiteCode(siteCode)) {
            tenantRepository.save(Tenant.builder()
                    .siteCode(siteCode)
                    .domainPattern(domain)
                    .name("Test Tenant " + siteCode)
                    .description("For Rate Limit Test")
                    .build());
        }
    }

    @Test
    @WithMockUser
    @DisplayName("테넌트별 동적 용량 적용: VIP 사이트는 설정된 높은 용량(1000)이 전달되어야 한다")
    void shouldUseCustomCapacityForVipTenant() throws Exception {
        // given
        properties.getPerTenantCapacities().put("vip-site.com", 1000);

        ConsumptionProbe successProbe = mock(ConsumptionProbe.class);
        given(successProbe.isConsumed()).willReturn(true);
        given(successProbe.getRemainingTokens()).willReturn(999L);

        given(rateLimitProvider.tryConsume(anyString(), anyInt())).willReturn(successProbe);

        // when
        mockMvc.perform(get("/api/test")
                        .header("X-Tenant-ID", "vip-site.com"))
                .andExpect(status().isNotFound()) // 404 Not Found (정상: 핸들러 없음)
                .andExpect(header().string("X-Rate-Limit-Remaining", "999"));

        // then [수정됨]
        // 실제 키는 "vip-site.com:127.0.0.1:/api/test" 형식이므로 startsWith로 검증
        verify(rateLimitProvider).tryConsume(startsWith("vip-site.com"), eq(1000));
    }

    @Test
    @WithMockUser
    @DisplayName("테넌트별 동적 용량 적용: 설정이 없는 일반 사이트는 기본 용량(100)이 전달되어야 한다")
    void shouldUseDefaultCapacityForNormalTenant() throws Exception {
        // given
        int defaultCapacity = properties.getCapacity(); // 보통 100

        ConsumptionProbe successProbe = mock(ConsumptionProbe.class);
        given(successProbe.isConsumed()).willReturn(true);
        given(successProbe.getRemainingTokens()).willReturn(99L);

        given(rateLimitProvider.tryConsume(anyString(), anyInt())).willReturn(successProbe);

        // when
        mockMvc.perform(get("/api/test")
                        .header("X-Tenant-ID", "normal-site.com"))
                .andExpect(status().isNotFound());

        // then [수정됨]
        // 용량이 defaultCapacity(100)으로 잘 들어갔는지 확인하는 것이 핵심
        verify(rateLimitProvider).tryConsume(startsWith("normal-site.com"), eq(defaultCapacity));
    }
}