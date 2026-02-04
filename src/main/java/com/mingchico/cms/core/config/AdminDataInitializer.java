package com.mingchico.cms.core.config;

import com.mingchico.cms.core.tenant.domain.Tenant;
import com.mingchico.cms.core.tenant.repository.TenantRepository;
import com.mingchico.cms.core.user.domain.Membership;
import com.mingchico.cms.core.user.domain.Role;
import com.mingchico.cms.core.user.domain.User;
import com.mingchico.cms.core.user.domain.UserStatus;
import com.mingchico.cms.core.user.repository.MembershipRepository;
import com.mingchico.cms.core.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h3>[관리자 데이터 부트스트래퍼]</h3>
 * <p>
 * 시스템 구동 시 {@link BootstrapProperties} 설정을 기반으로
 * 슈퍼 관리자 계정과 관리자 전용 테넌트를 자동으로 구성합니다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    // [Refactoring] @Value 대신 Type-Safe 프로퍼티 객체 사용
    private final BootstrapProperties properties;

    @Override
    @Transactional
    public void run(String... args) {
        BootstrapProperties.AdminUser adminProp = properties.getAdmin();
        BootstrapProperties.SystemTenant tenantProp = properties.getTenant();

        // 1. 이메일 중복 체크
        if (userRepository.existsByEmail(adminProp.getEmail())) {
            log.info("✅ Admin account '{}' already exists. Skipping initialization.", adminProp.getEmail());
            return;
        }

        log.info("🚀 Initializing Super Admin Context...");

        // 2. 슈퍼 관리자 User 생성
        User admin = User.builder()
                .email(adminProp.getEmail())
                .password(passwordEncoder.encode(adminProp.getPassword()))
                .nickname(adminProp.getNickname())
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(admin);

        // 3. 관리자 전용 테넌트 생성 (설정파일에서 정의한 값 사용)
        // 예: siteCode="SECRET_ADMIN", domain="ops.example.com"
        Tenant adminTenant = tenantRepository.findBySiteCode(tenantProp.getSiteCode())
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .siteCode(tenantProp.getSiteCode())
                        .domainPattern(tenantProp.getDomain())
                        .name(tenantProp.getName())
                        .description("System Administration Workspace")
                        // [Step 1] 이후 themeName 필드 추가 시 주석 해제
                        // .themeName("admin-theme")
                        .build()));

        // 4. 멤버십 연결 (User + Tenant + Role.ADMIN)
        Membership membership = Membership.builder()
                .user(admin)
                .tenant(adminTenant)
                .role(Role.ADMIN)
                .build();
        membershipRepository.save(membership);

        log.info("✨ Admin Bootstrap Complete!");
        log.info("   - User: {}", adminProp.getEmail());
        log.info("   - Access URL: http://{}", tenantProp.getDomain());
        log.info("   - Site Code: {}", tenantProp.getSiteCode());
    }
}