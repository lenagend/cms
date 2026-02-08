package com.mingchico.cms.core.security;

import com.mingchico.cms.core.context.ContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * <h3>[기본 보안 컨텍스트 구현체]</h3>
 * <p>
 * 현재 시스템의 표준인 {@link ContextHolder} (Spring SecurityContext + ThreadLocal)를
 * 사용하여 보안 정보를 제공합니다.
 * <br>
 * 나중에 SSO나 외부 인증으로 변경 시, 이 클래스 대신
 * 'SsoAccessContext' 등을 새로 만들어 @Primary로 등록하면 됩니다.
 * </p>
 */
@Component // [중요] 스프링 빈으로 등록되어야 주입 가능
@RequiredArgsConstructor
public class DefaultAccessContext implements AccessContext {

    @Override
    public Optional<String> getCurrentUserEmail() {
        return ContextHolder.getUser()
                .map(CustomUserDetails::getEmail);
    }

    @Override
    public boolean isAuthenticated() {
        return ContextHolder.isAuthenticated();
    }

    @Override
    public boolean hasRole(String role) {
        return ContextHolder.hasRole(role);
    }
}