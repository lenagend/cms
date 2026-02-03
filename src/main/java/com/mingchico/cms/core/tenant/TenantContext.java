package com.mingchico.cms.core.tenant;

import com.mingchico.cms.core.tenant.dto.TenantInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * <h3>[테넌트 컨텍스트 (Tenant Context)]</h3>
 * <p>
 * 현재 요청을 처리 중인 스레드(Thread)에 "현재 사이트 코드(Site Code)"를 바인딩하여 관리합니다.
 * {@link ThreadLocal}을 사용하여, 파라미터로 사이트 코드를 계속 넘기지 않아도
 * 서비스, 리포지토리 어디서든 현재 접속 중인 사이트 정보를 알 수 있게 합니다.
 * </p>
 */
@Slf4j
public class TenantContext {

    private static final ThreadLocal<String> CURRENT_SITE_CODE = new ThreadLocal<>();
    private static final ThreadLocal<TenantInfo> CURRENT_TENANT = new ThreadLocal<>();


    public static TenantInfo getTenant() {
        return CURRENT_TENANT.get();
    }
    /**
     * 테넌트 정보를 컨텍스트에 바인딩합니다.
     * @param info 캐시된 테넌트 메타데이터
     */
    public static void setContext(TenantInfo info) {
        if (info == null) return;
        CURRENT_SITE_CODE.set(info.siteCode());
        CURRENT_TENANT.set(info);
    }

    public static String getSiteCode() {
        return CURRENT_SITE_CODE.get();
    }

    public static boolean isMaintenanceMode() {
        TenantInfo info = CURRENT_TENANT.get();
        return info != null && info.maintenance();
    }

    public static boolean isReadOnlyMode() {
        TenantInfo info = CURRENT_TENANT.get();
        return info != null && (info.readOnly() || info.maintenance());
    }
    /**
     * [컨텍스트 초기화]
     * 스레드 풀(Thread Pool) 환경에서는 스레드가 재사용되므로,
     * 요청 처리가 끝난 후 반드시 비워주어야 '데이터 오염'을 막을 수 있습니다.
     */
    public static void clear() {
        CURRENT_SITE_CODE.remove();
        CURRENT_TENANT.remove();
    }
}