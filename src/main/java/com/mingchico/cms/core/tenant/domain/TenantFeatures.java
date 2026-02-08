package com.mingchico.cms.core.tenant.domain;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * <h3>[테넌트 기능 설정 (Feature Flags)]</h3>
 * <p>
 * Tenant 엔티티의 JSON 컬럼에 저장되는 설정값입니다.
 * 사이트별로 기능을 켜고 끄는 <b>스위치 역할만 수행</b>합니다.
 * </p>
 *
 * <h3>🚫 주의사항 (Anti-Pattern)</h3>
 * <ul>
 * <li><b>비즈니스 로직 포함 금지:</b> {@code canWritePost()} 같은 로직은 서비스 레이어에 있어야 합니다.</li>
 * <li><b>테마와 혼동 금지:</b> 디자인(CSS)과 기능(Feature)은 분리되어야 합니다.</li>
 * </ul>
 */
@Getter
@Setter
public class TenantFeatures implements Serializable {

    // --- [Core Modules] ---

    // 게시판 모듈 사용 여부 (메뉴 노출 제어)
    private boolean boardModuleEnabled = true;

    // 쇼핑몰 모듈 사용 여부 (미래 확장)
    private boolean shopModuleEnabled = false;

    // --- [Global Components] ---

    // 헤더 로그인 버튼 노출 여부
    private boolean loginVisible = true;

    // 사이트 전역 팝업 사용 여부
    private boolean popupEnabled = false;

    // 1:1 문의 기능 사용 여부
    private boolean inquiryEnabled = true;

    // --- [Presets] (편의상 정적 팩토리 제공 가능) ---

    public static TenantFeatures createDefault() {
        return new TenantFeatures();
    }

    public static TenantFeatures createMinimal() {
        TenantFeatures f = new TenantFeatures();
        f.setBoardModuleEnabled(false);
        f.setShopModuleEnabled(false);
        f.setLoginVisible(false);
        return f;
    }
}