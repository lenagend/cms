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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private DomainTenantResolver domainTenantResolver;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 캐시 및 DB 초기화
        tenantRepository.deleteAll();

        // 시스템 관리자 테넌트 생성 (기본)
        tenantRepository.save(Tenant.builder()
                .domainPattern("localhost")
                .siteCode("SYSTEM_ADMIN")
                .name("System Admin")
                .description("Default Admin Tenant")
                .build());

        // [중요] 테스트 시작 전 캐시를 DB와 동기화 (이전 테스트 영향 제거)
        domainTenantResolver.refreshRules();
    }

    @Test
    @DisplayName("신규 테넌트 등록 후 접속 테스트 (트랜잭션 내 캐시 갱신 검증)")
    @WithMockUser(username = "admin@system.com", roles = "ADMIN")
    void tenant_full_flow_test() throws Exception {
        // 1. [등록] 관리자 API 호출
        TenantDto.CreateRequest createRequest = new TenantDto.CreateRequest(
                "domain-a.com", "SITE_A", "A Corp", "Description"
        );

        mockMvc.perform(post("/api/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // 2. [수동 갱신] @Transactional 테스트는 커밋이 안 되므로
        // 'AFTER_COMMIT' 이벤트가 발생하지 않습니다. 수동으로 캐시를 갱신해줍니다.
        domainTenantResolver.refreshRules();

        // 3. [검증] 등록된 도메인으로 접속 시 정상 식별되는지 확인
        // (관리자 API는 필터 제외 경로일 수 있으므로 테스트용 컨트롤러 사용 권장)
        mockMvc.perform(get("/test/tenant-check")
                        .header("Host", "domain-a.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("SITE_A"));
    }

    @Test
    @DisplayName("유지보수 모드인 테넌트 접속 시 503 에러 반환")
    void maintenance_mode_access_denied() throws Exception {
        // 1. 유지보수 모드 테넌트 생성
        Tenant tenant = Tenant.builder()
                .domainPattern("repair.com")
                .siteCode("SITE_REPAIR")
                .name("Repair Site")
                .description("Under Construction")
                .build();

        // 엔티티 메서드로 상태 변경 (또는 빌더에 필드 추가)
        tenant.setMaintenance(true);
        tenantRepository.save(tenant);

        // 2. 캐시 갱신
        domainTenantResolver.refreshRules();

        // 3. 접속 시도 -> 503 Service Unavailable 기대
        mockMvc.perform(get("/test/tenant-check")
                        .header("Host", "repair.com"))
                .andExpect(status().isServiceUnavailable()) // 503 확인
                .andExpect(jsonPath("$.message").value("시스템 점검 중입니다."));
    }

    @Test
    @DisplayName("미등록 도메인 접속 시 404 에러 반환")
    void unknown_domain_rejection() throws Exception {
        mockMvc.perform(get("/test/tenant-check")
                        .header("Host", "unknown.com"))
                .andExpect(status().isNotFound());
    }
}