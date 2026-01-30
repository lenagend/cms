package com.mingchico.cms.core.tenant;

import com.mingchico.cms.core.tenant.TenantResolver.UnknownTenantException;
import com.mingchico.cms.core.tenant.domain.Tenant;
import com.mingchico.cms.core.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DomainTenantResolverTest {

    @InjectMocks
    private DomainTenantResolver tenantResolver;

    @Mock
    private TenantRepository tenantRepository;

    @BeforeEach
    void setUp() {
        // [Given] 테스트용 DB 데이터 모킹
        // DB에 이런 규칙들이 있다고 가정하고 메모리에 로드
        given(tenantRepository.findAllByOrderByDomainPatternDesc()).willReturn(List.of(
                Tenant.builder().domainPattern("mingchico.com").siteCode("SITE_MAIN").build(),
                Tenant.builder().domainPattern("*.mingchico.com").siteCode("SITE_SUB").build(),
                Tenant.builder().domainPattern("admin.mingchico.com").siteCode("SITE_ADMIN").build()
        ));

        // @PostConstruct가 Mockito 환경에서는 자동 실행 안 되므로 수동 호출
        tenantResolver.refreshRules();
    }

    @Test
    @DisplayName("정확히 일치하는 도메인을 찾는다 (O(1))")
    void resolve_exact_match() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("mingchico.com");

        // When
        String siteCode = tenantResolver.resolveSiteCode(request);

        // Then
        assertThat(siteCode).isEqualTo("SITE_MAIN");
    }

    @Test
    @DisplayName("서브 도메인은 와일드카드 규칙에 매칭된다")
    void resolve_wildcard_match() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("shop.mingchico.com");

        // When
        String siteCode = tenantResolver.resolveSiteCode(request);

        // Then
        assertThat(siteCode).isEqualTo("SITE_SUB");
    }

    @Test
    @DisplayName("더 구체적인 도메인(admin)이 와일드카드(*)보다 우선한다")
    void resolve_specific_priority() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("admin.mingchico.com");
        // *.mingchico.com 에도 걸리지만, 정확한 일치(admin.mingchico.com)가 먼저임

        // When
        String siteCode = tenantResolver.resolveSiteCode(request);

        // Then
        assertThat(siteCode).isEqualTo("SITE_ADMIN");
    }

    @Test
    @DisplayName("등록되지 않은 도메인은 예외를 던진다")
    void resolve_unknown_domain() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("hacker.com");

        // When & Then
        assertThatThrownBy(() -> tenantResolver.resolveSiteCode(request))
                .isInstanceOf(UnknownTenantException.class)
                .hasMessageContaining("hacker.com");
    }

    @Test
    @DisplayName("개발자 헤더(X-Tenant-ID)가 있으면 DB보다 우선한다")
    void resolve_header_override() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("mingchico.com"); // 원래는 SITE_MAIN
        request.addHeader("X-Tenant-ID", "SITE_TEST");

        // When
        String siteCode = tenantResolver.resolveSiteCode(request);

        // Then
        assertThat(siteCode).isEqualTo("SITE_TEST");
    }
}