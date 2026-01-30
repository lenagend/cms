package com.mingchico.cms.core.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mingchico.cms.core.tenant.domain.Tenant;
import com.mingchico.cms.core.tenant.dto.TenantDto;
import com.mingchico.cms.core.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * <h3>[테넌트 통합 기능 테스트]</h3>
 * <p>
 * 리포지토리부터 컨트롤러, 필터, 보안 설정까지 전체 시스템이 유기적으로 동작하는지 검증합니다.
 * 단위 테스트로 쪼개지 않고, 실제 HTTP 요청 흐름을 따라 전반적인 기능을 체크합니다.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional // 테스트 후 DB 롤백
class TenantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private DomainTenantResolver domainTenantResolver;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        tenantRepository.deleteAll();
        // [수정] 빌더에 name() 필드를 추가하여 NOT NULL 제약을 충족시킵니다.
        tenantRepository.save(Tenant.builder()
                .domainPattern("localhost")
                .siteCode("SYSTEM_ADMIN")
                .name("시스템 관리자")
                .description("시스템 관리용")
                .build());

        domainTenantResolver.refreshRules();
    }

    @Test
    @DisplayName("신규 테넌트 등록부터 도메인 접속 식별까지의 전체 흐름 테스트")
    @WithMockUser(username = "admin@system.com", roles = "ADMIN")
    void tenant_full_flow_test() throws Exception {
        // 1. [등록] 관리자가 새로운 테넌트(domain-a.com)를 등록함
        TenantDto.CreateRequest createRequest = new TenantDto.CreateRequest(
                "domain-a.com",
                "SITE_A",
                "A사 공식 사이트",
                "설명"
        );

        mockMvc.perform(post("/api/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.siteCode").value("SITE_A"))
                .andExpect(jsonPath("$.createdBy").value("admin@system.com"));
        // DB에 실제로 잘 저장되었는지 확인
        assertThat(tenantRepository.existsByDomainPattern("domain-a.com")).isTrue();

        // 2. [식별] 등록된 도메인으로 접속했을 때 시스템이 SITE_A로 인식하는지 확인
        // 이 과정에서 TenantFilter -> TenantResolver -> DB 조회 -> TenantContext 주입이 일어남
        mockMvc.perform(get("/api/admin/tenants") // 권한이 있는 API 아무거나 호출
                        .header("Host", "domain-a.com"))
                .andDo(result -> {
                    // 필터 통과 시점에 Context나 MDC에 값이 박혔는지는 내부 로그나 별도 인터셉터로 확인 가능
                    // 여기서는 필터가 404(Unknown Tenant)를 뱉지 않고 200을 뱉는 것으로 간접 확인
                    assertThat(result.getResponse().getStatus()).isEqualTo(200);
                });
    }

    @Test
    @DisplayName("와일드카드 도메인 등록 후 일반 API 접속 시 테넌트 식별 검증")
    @WithMockUser(username = "admin@system.com", roles = "ADMIN")
    void wildcard_identification_success() throws Exception {
        // 1. 테넌트 등록
        TenantDto.CreateRequest request = new TenantDto.CreateRequest("*.mingchico.com", "SITE_WC", "테스트사이트","와일드카드");
        mockMvc.perform(post("/api/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // 2. [검증] 일반 API 경로로 접속 시도 (blog.mingchico.com)
        mockMvc.perform(get("/test/tenant-check")
                        .header("Host", "blog.mingchico.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("SITE_WC"));
    }

    @Test
    @DisplayName("등록되지 않은 도메인으로 접속 시 Filter에서 404 차단 검증")
    void unknown_domain_rejection() throws Exception {
        // Given: 아무것도 등록되지 않은 상태

        // When & Then: hacker.com으로 접속 시도
        mockMvc.perform(get("/test/tenant-check")
                        .header("Host", "hacker.com"))
                .andExpect(status().isNotFound()) // TenantFilter에서 발생시킨 404
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("테넌트 관리 정보(이름/설명) 수정 및 캐시 반영 테스트")
    @WithMockUser(username = "admin@system.com", roles = "ADMIN")
    void update_tenant_and_cache_refresh_test() throws Exception {
        // 1. 기존 테넌트 생성
        Tenant saved = tenantRepository.save(Tenant.builder()
                .domainPattern("update.com")
                .siteCode("BEFORE") // 사이트 코드는 고정
                .name("수정전 사이트명")
                .build());

        // 2. 수정 요청 (이름과 설명만 수정)
        TenantDto.UpdateRequest updateRequest = new TenantDto.UpdateRequest(
                "새이름",
                "설명수정"
        );

        mockMvc.perform(put("/api/admin/tenants/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                // [수정] 사이트 코드는 바뀌지 않았음을 검증 (BEFORE 유지)
                .andExpect(jsonPath("$.siteCode").value("BEFORE"))
                // [수정] 이름이 정상적으로 변경되었는지 검증
                .andExpect(jsonPath("$.name").value("새이름"));

        // 3. 캐시 반영 확인
        mockMvc.perform(get("/api/admin/tenants")
                        .header("Host", "update.com"))
                .andExpect(status().isOk());
    }
}