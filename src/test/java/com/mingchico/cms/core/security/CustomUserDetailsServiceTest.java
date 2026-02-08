package com.mingchico.cms.core.security;

import com.mingchico.cms.core.tenant.TenantContext;
import com.mingchico.cms.core.tenant.domain.Tenant;
import com.mingchico.cms.core.tenant.domain.TenantFeatures;
import com.mingchico.cms.core.tenant.dto.TenantInfo; // [Import 추가]
import com.mingchico.cms.core.user.domain.Membership;
import com.mingchico.cms.core.user.domain.Role;
import com.mingchico.cms.core.user.domain.User;
import com.mingchico.cms.core.user.domain.UserStatus;
import com.mingchico.cms.core.user.repository.MembershipRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * [CustomUserDetailsService 단위 테스트]
 * SaaS 멀티 테넌트 환경에서의 사용자 인증 로직을 독립적으로 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private MembershipRepository membershipRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * [성공 케이스]
     * TenantContext.setContext(TenantInfo)를 사용하여 테넌트 정보를 설정합니다.
     */
    @Test
    @DisplayName("성공: CustomUserDetails가 반환되며, 사이트 코드와 이메일이 정확히 매핑된다")
    void loadUserByUsername_success() {
        // Given
        String email = "test@mingchico.com";
        String siteCode = "site-a";

        // [수정] setSiteCode(String) -> setContext(TenantInfo)로 변경
        // 테스트용 더미 TenantInfo 객체 생성 (ID 1L, 이름 "Test Site", 유지보수/읽기전용 false)
        TenantInfo tenantInfo = new TenantInfo(1L, siteCode, "Test Site", "testThemeName", false, false, new TenantFeatures());
        TenantContext.setContext(tenantInfo);

        User mockUser = User.builder()
                .email(email)
                .password("encodedPw")
                .nickname("Tester")
                .status(UserStatus.ACTIVE)
                .build();

        Membership mockMembership = Membership.builder()
                .user(mockUser)
                .tenant(Tenant.builder().siteCode(siteCode).build())
                .role(Role.ADMIN)
                .status(Membership.MembershipStatus.ACTIVE)
                .build();

        given(membershipRepository.findActiveMembership(email, siteCode))
                .willReturn(Optional.of(mockMembership));

        // When
        UserDetails result = userDetailsService.loadUserByUsername(email);

        // Then
        assertThat(result).isInstanceOf(CustomUserDetails.class);

        CustomUserDetails customUser = (CustomUserDetails) result;
        assertThat(customUser.getUsername()).isEqualTo(email);
        assertThat(customUser.getSiteCode()).isEqualTo(siteCode);
        assertThat(customUser.getRole()).isEqualTo(Role.ADMIN);
        assertThat(customUser.getAuthorities()).hasSize(1);
        assertThat(customUser.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("실패: 테넌트 정보(Context)가 없으면 예외가 발생한다")
    void fail_no_tenant_context() {
        // [1] TenantContext 비우기
        TenantContext.clear();

        // [2] 실행 및 검증
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("test@email.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("테넌트 정보를 확인할 수 없습니다"); // 예외 메시지는 실제 구현에 맞게 조정
    }

    @Test
    @DisplayName("실패: 해당 테넌트에 멤버십이 없으면 예외가 발생한다")
    void fail_no_membership() {
        // [1] Given: 사이트 접속 상태 설정 (site-b)
        String siteCode = "site-b";

        // [수정] setContext를 통해 TenantInfo 주입
        TenantInfo tenantInfo = new TenantInfo(2L, siteCode, "Other Site", "testThemeName", false, false, new TenantFeatures());
        TenantContext.setContext(tenantInfo);

        given(membershipRepository.findActiveMembership(anyString(), anyString()))
                .willReturn(Optional.empty());

        // [2] When & Then
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("stranger@email.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("not found or not a member");
    }
}