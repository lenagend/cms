package com.mingchico.cms.core.security;

import com.mingchico.cms.core.user.domain.Role;
import com.mingchico.cms.core.user.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * <h3>[CMS 전용 사용자 세부 정보 (Principal)]</h3>
 * <p>
 * Spring Security 인증 과정에서 사용자를 식별하는 핵심 객체입니다.
 * SaaS 환경에서 <b>'이메일 + 사이트 코드'</b>의 복합 키를 기준으로 사용자를 식별하여,
 * A 사이트의 로그인이 B 사이트의 세션에 영향을 주지 않도록 격리합니다.
 * </p>
 */
@Getter
public class CustomUserDetails implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String email;
    private final String password;
    private final String nickname;
    
    /**
     * [필수] 테넌트 식별자
     * SessionRegistry에서 동시 접속자를 카운팅할 때 구분 기준이 됩니다.
     */
    private final String siteCode;
    
    private final Role role;
    private final Collection<? extends GrantedAuthority> authorities;

    // 계정 상태 플래그
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final boolean accountNonExpired;
    private final boolean credentialsNonExpired;

    public CustomUserDetails(User user, String siteCode, Role role, boolean enabled, boolean accountNonLocked) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.nickname = user.getNickname();
        this.siteCode = siteCode;
        this.role = role;
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority(role.getKey()));

        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.accountNonExpired = true;
        this.credentialsNonExpired = true;
    }

    /**
     * <h3>[동시성 제어의 핵심 로직]</h3>
     * SessionRegistry는 이 메서드의 결과를 Map의 Key로 사용합니다.
     * 이메일과 사이트 코드를 모두 비교해야만 테넌트별 독립 세션이 가능합니다.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomUserDetails that = (CustomUserDetails) o;
        return Objects.equals(email, that.email) &&
               Objects.equals(siteCode, that.siteCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, siteCode);
    }

    // --- UserDetails 구현 ---

    @Override
    public String getUsername() { return email; } // Principal Name

    @Override
    public boolean isAccountNonExpired() { return accountNonExpired; }
    @Override
    public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override
    public boolean isCredentialsNonExpired() { return credentialsNonExpired; }
    @Override
    public boolean isEnabled() { return enabled; }
}