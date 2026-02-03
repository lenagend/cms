package com.mingchico.cms.core.tenant.dto;

import java.io.Serializable;

/**
 * <h3>[테넌트 메타데이터]</h3>
 * <p>
 * 필터(Filter) 레벨에서 빈번하게 조회되는 테넌트의 핵심 정보를 담는 경량 객체입니다.
 * DB 엔티티 대신 이 객체를 캐싱(Local/Redis)하여 성능을 최적화합니다.
 * </p>
 */
public record TenantInfo(
        Long id,
        String siteCode,
        String name,
        boolean maintenance,
        boolean readOnly
) implements Serializable {
    // Redis 직렬화(Serialization)를 위해 Serializable 인터페이스 구현
}