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
 * 이메일(ID)뿐만 아니라 <b>현재 접속 경로(Tenant)</b>에 유효한 멤버십이 있는지 검증하고,
 * 테넌트 정보가 포함된 CustomUserDetails를 반환합니다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final MembershipRepository membershipRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // 1. 테넌트 컨텍스트 확인
        String currentSiteCode = TenantContext.getSiteCode();
        if (!StringUtils.hasText(currentSiteCode)) {
            log.error("Login Failed: Tenant Context is missing.");
            throw new UsernameNotFoundException("접속 경로의 테넌트 정보를 확인할 수 없습니다.");
        }

        // 2. 멤버십 조회 (Tenant + Email)
        Membership membership = membershipRepository.findActiveMembership(email, currentSiteCode)
                .orElseThrow(() -> new UsernameNotFoundException(
                        String.format("User '%s' not found or not a member of '%s'", email, currentSiteCode)));

        User user = membership.getUser();

        //TODO: lastLoginAt 업데이트

        // 3. 상태 검증
        boolean enabled = (user.getStatus() == UserStatus.ACTIVE && membership.getStatus() == MembershipStatus.ACTIVE);
        boolean nonLocked = (user.getStatus() != UserStatus.LOCKED && membership.getStatus() != MembershipStatus.BANNED);

        // 4. CustomUserDetails 반환
        return new CustomUserDetails(
                user,
                currentSiteCode,
                membership.getRole(),
                enabled,
                nonLocked
        );
    }
}