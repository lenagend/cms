package com.mingchico.cms.core.security;

import com.mingchico.cms.core.tenant.TenantContext;
import com.mingchico.cms.core.user.domain.Membership;
import com.mingchico.cms.core.user.domain.Membership.MembershipStatus;
import com.mingchico.cms.core.user.domain.User;
import com.mingchico.cms.core.user.domain.UserStatus;
import com.mingchico.cms.core.user.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * <h3>[SaaS 사용자 인증 서비스]</h3>
 * <p>
 * 1. <b>식별:</b> 이메일(Principal) + 현재 사이트 코드(Context)
 * 2. <b>인증:</b> 해당 조합의 Membership 존재 여부 확인
 * 3. <b>인가:</b> Membership에 정의된 Role 부여
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final MembershipRepository membershipRepository;

    /**
     * @param email 로그인 폼에서 입력받은 이메일 (Spring Security에서는 변수명이 username임)
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // 1. 현재 접속한 사이트 코드 (TenantFilter -> ThreadLocal)
        String currentSiteCode = TenantContext.getSiteCode();

        if (!StringUtils.hasText(currentSiteCode)) {
            throw new UsernameNotFoundException("접속 경로의 테넌트 정보를 확인할 수 없습니다.");
        }

        // 2. [인증] 이메일 + 사이트코드로 멤버십 조회
        Membership membership = membershipRepository.findActiveMembership(email, currentSiteCode)
                .orElseThrow(() -> new UsernameNotFoundException(
                        String.format("User '%s' not found or not a member of '%s'", email, currentSiteCode)));

        User user = membership.getUser();

        // 3. [상태 체크 & Audit] - 실제 구현 시엔 여기서 lastLoginAt 업데이트 이벤트 발행 권장

        // 4. UserDetails 반환
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail()) // Principal은 Email
                .password(user.getPassword())

                // [권한 부여]
                // 현재 접속한 TenantContext 하에서의 권한만 부여합니다.
                .roles(membership.getRole().name())

                .disabled(user.getStatus() != UserStatus.ACTIVE || membership.getStatus() != MembershipStatus.ACTIVE)
                .accountLocked(user.getStatus() == UserStatus.LOCKED || membership.getStatus() == MembershipStatus.BANNED)
                .accountExpired(false)
                .credentialsExpired(false)
                .build();
    }
}