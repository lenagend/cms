package com.mingchico.cms.core.security;

import com.mingchico.cms.core.tenant.TenantContext;
import com.mingchico.cms.core.tenant.domain.Tenant;
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
 * <p>
 * <b>검증 항목:</b>
 * 1. ThreadLocal(TenantContext)에 저장된 사이트 코드를 이용한 멤버십 조회 로직
 * 2. 특정 테넌트에 소속된 사용자의 권한(Role) 및 계정 상태(Status) 변환 여부
 * 3. 테넌트 정보가 없거나 멤버십이 존재하지 않는 경우의 예외 처리(UsernameNotFoundException)
 * </p>
 */
@ExtendWith(MockitoExtension.class) // Mockito 가짜 객체 사용을 위한 확장
class CustomUserDetailsServiceTest {

    @Mock // 가짜 DB 레포지토리 객체 생성
    private MembershipRepository membershipRepository;

    @InjectMocks // 가짜 객체(Mock)들을 주입받아 실제로 테스트할 서비스 객체
    private CustomUserDetailsService userDetailsService;

    @AfterEach
    void tearDown() {
        // [ThreadLocal 정리] 테스트는 같은 스레드에서 돌 수 있으므로 이전 테스트의 테넌트 정보를 제거
        TenantContext.clear();
    }

    /**
     * [성공 케이스]
     * 테넌트 정보가 있고 멤버십이 존재하는 경우 UserDetails 객체가 정확히 생성되는지 확인합니다.
     */
    @Test
    @DisplayName("정상: 현재 테넌트에 멤버십이 있는 유저는 조회에 성공한다")
    void loadUserByUsername_success() {
        // [1] 데이터 세팅 (Given)
        String email = "test@mingchico.com";
        String siteCode = "site-a";

        // 필터가 해주는 테넌트 설정 과정을 수동으로 재현
        TenantContext.setSiteCode(siteCode);

        // 가짜 유저 엔티티 생성
        User mockUser = User.builder()
                .email(email)
                .password("encodedPw")
                .nickname("Tester")
                .status(UserStatus.ACTIVE)
                .build();

        // 가짜 멤버십 엔티티 생성 (유저 + 사이트 연결)
        Membership mockMembership = Membership.builder()
                .user(mockUser)
                .tenant(Tenant.builder().siteCode(siteCode).build())
                .role(Role.ADMIN)
                .status(Membership.MembershipStatus.ACTIVE)
                .build();

        // 레포지토리가 이메일과 사이트코드로 조회 시 가짜 멤버십을 반환하도록 설정
        given(membershipRepository.findActiveMembership(email, siteCode))
                .willReturn(Optional.of(mockMembership));

        // [2] 로직 실행 (When)
        // 시큐리티가 인증 시 사용하는 메서드 호출
        UserDetails details = userDetailsService.loadUserByUsername(email);

        // [3] 결과 검증 (Then)
        assertThat(details.getUsername()).isEqualTo(email); // 이메일 일치 확인
        assertThat(details.getPassword()).isEqualTo("encodedPw"); // 비밀번호 일치 확인
        assertThat(details.getAuthorities()).hasSize(1); // 권한 개수 확인
        // "ROLE_" 접두사가 붙은 권한이 정상 부여되었는지 확인
        assertThat(details.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_ADMIN");
    }

    /**
     * [실패 케이스 - 테넌트 미식별]
     * URL 등을 통해 사이트 정보(siteCode)를 알 수 없는 경우 인증을 거절해야 합니다.
     */
    @Test
    @DisplayName("실패: 테넌트 정보(Context)가 없으면 예외가 발생한다")
    void fail_no_tenant_context() {
        // [1] 상황 재현: 테넌트 정보를 아예 설정하지 않음 (Given)
        TenantContext.clear();

        // [2] 실행 및 검증 (When & Then)
        // 로직 실행 시 UsernameNotFoundException이 던져지는지 확인
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("test@email.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("테넌트 정보를 확인할 수 없습니다");
    }

    /**
     * [실패 케이스 - 멤버십 미존재]
     * 시스템에 등록된 유저라도 해당 사이트에 가입(멤버십)되지 않았다면 로그인을 막아야 합니다.
     */
    @Test
    @DisplayName("실패: 해당 테넌트에 멤버십이 없으면 예외가 발생한다")
    void fail_no_membership() {
        // [1] 상황 재현: 사이트 접속은 했으나 멤버십 조회 결과가 없는 상태 (Given)
        TenantContext.setSiteCode("site-b");
        given(membershipRepository.findActiveMembership(anyString(), anyString()))
                .willReturn(Optional.empty());

        // [2] 실행 및 검증 (When & Then)
        // 멤버십이 없으면 "not a member" 메시지와 함께 예외 발생 확인
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("stranger@email.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("not found or not a member");
    }
}