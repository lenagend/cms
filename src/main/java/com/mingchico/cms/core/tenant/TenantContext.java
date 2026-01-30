package com.mingchico.cms.core.tenant;

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

    /**
     * 현재 스레드에 사이트 코드를 저장합니다.
     * @param siteCode 식별된 사이트 코드 (예: "SITE_A")
     */
    public static void setSiteCode(String siteCode) {
        if (!StringUtils.hasText(siteCode)) {
            // [방어 로직] 빈 값은 저장하지 않음 (이전 값이 남아있는지 확인 필요)
            return;
        }
        CURRENT_SITE_CODE.set(siteCode);
    }

    /**
     * @return 현재 스레드에 바인딩된 사이트 코드 (없으면 null 반환)
     */
    public static String getSiteCode() {
        return CURRENT_SITE_CODE.get();
    }

    /**
     * [컨텍스트 초기화]
     * 스레드 풀(Thread Pool) 환경에서는 스레드가 재사용되므로,
     * 요청 처리가 끝난 후 반드시 비워주어야 '데이터 오염'을 막을 수 있습니다.
     */
    public static void clear() {
        CURRENT_SITE_CODE.remove();
    }
}