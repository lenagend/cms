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
import org.springframework.util.AntPathMatcher;

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

    // 생성자 주입 시 AntPathMatcher 등이 필요하므로 수동 주입 혹은 Spy 사용 고려
    // 여기서는 @InjectMocks가 생성자를 호출할 때 필요한 인자를 매칭해주지 못할 수 있어 수동 세팅
    @BeforeEach
    void setUp() {
        // DomainTenantResolver는 생성자 주입을 받으므로,
        // Mockito의 @InjectMocks 대신 직접 생성하여 명확하게 테스트 환경 구성
        tenantResolver = new DomainTenantResolver(tenantRepository);

        // [Given] 테스트용 DB 데이터 모킹
        // DB에서 순서가 뒤죽박죽으로 넘어와도, 자바 로직이 정렬을 잘 하는지 테스트하기 위해 일부러 섞음
        given(tenantRepository.findAll()).willReturn(List.of(
                Tenant.builder().domainPattern("*.mingchico.com").siteCode("SITE_WILD").build(), // 1. 와일드카드
                Tenant.builder().domainPattern("admin.mingchico.com").siteCode("SITE_ADMIN").build(), // 2. 구체적 (우선순위 높아야 함)
                Tenant.builder().domainPattern("mingchico.com").siteCode("SITE_MAIN").build() // 3. 정확 일치
        ));

        // 캐시 초기화 (DB 로딩 시뮬레이션)
        tenantResolver.refreshRules();
    }

    @Test
    @DisplayName("정확히 일치하는 도메인(Exact Match)이 최우선이다")
    void resolve_exact_match() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("mingchico.com");

        String siteCode = tenantResolver.resolveSiteCode(request);

        assertThat(siteCode).isEqualTo("SITE_MAIN");
    }

    @Test
    @DisplayName("서브 도메인은 와일드카드 규칙에 매칭된다")
    void resolve_wildcard_match() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("shop.mingchico.com");

        String siteCode = tenantResolver.resolveSiteCode(request);

        assertThat(siteCode).isEqualTo("SITE_WILD");
    }

    @Test
    @DisplayName("스마트 정렬 검증: 더 구체적인 도메인(admin)이 와일드카드(*)보다 우선한다")
    void resolve_specific_priority() {
        // Given: admin.mingchico.com 은 *.mingchico.com 에도 포함되지만,
        // 명시적으로 정의된 admin 규칙이 우선해야 함.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("admin.mingchico.com");

        // When
        String siteCode = tenantResolver.resolveSiteCode(request);

        // Then
        assertThat(siteCode).isEqualTo("SITE_ADMIN");
    }

    @Test
    @DisplayName("등록되지 않은 도메인은 예외를 던진다")
    void resolve_unknown_domain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("hacker.com");

        assertThatThrownBy(() -> tenantResolver.resolveSiteCode(request))
                .isInstanceOf(UnknownTenantException.class)
                .hasMessageContaining("hacker.com");
    }

    @Test
    @DisplayName("개발자 헤더(X-Tenant-ID)가 있으면 로직을 무시하고 강제 적용한다")
    void resolve_header_override() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("mingchico.com");
        request.addHeader("X-Tenant-ID", "SITE_TEST");

        String siteCode = tenantResolver.resolveSiteCode(request);

        assertThat(siteCode).isEqualTo("SITE_TEST");
    }
}