package com.mingchico.cms.core.tenant.dto;

import com.mingchico.cms.core.tenant.domain.TenantFeatures;
import java.io.Serializable;

public record TenantInfo(
        Long id,
        String siteCode,
        String name,
        String themeName,
        boolean maintenance,
        boolean readOnly,
        // [New] 뷰 템플릿 제어용 기능 플래그
        TenantFeatures features
) implements Serializable {
}