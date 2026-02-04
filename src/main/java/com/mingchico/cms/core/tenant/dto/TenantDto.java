package com.mingchico.cms.core.tenant.dto;

import com.mingchico.cms.core.tenant.domain.Tenant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * <h3>[테넌트 관리 DTO]</h3>
 */
public class TenantDto {

    /**
     * [등록 요청]
     */
    public record CreateRequest(
            @NotBlank(message = "도메인 패턴은 필수입니다.")
            @Pattern(regexp = "^[a-zA-Z0-9.*-]+$", message = "도메인은 영문, 숫자, 하이픈(-), 와일드카드(*)만 가능합니다.")
            String domainPattern,

            @NotBlank(message = "사이트 코드는 필수입니다.")
            @Pattern(regexp = "^[A-Z0-9_]+$", message = "사이트 코드는 대문자와 언더바(_)만 가능합니다.")
            String siteCode,

            @NotBlank(message = "사이트 이름은 필수입니다.")
            String name,

            String description
    ) {}

    /**
     * [수정 요청]
     * 도메인 패턴은 PK 개념에 가까우므로 수정 불가, 사이트 코드와 설명만 수정 가능하도록 설계
     */
    public record UpdateRequest(
            @NotBlank(message = "사이트 이름은 필수입니다.")
            String name,
            String description,
            String themeName
    ) {}

    /**
     * [응답]
     */
    public record Response(
            Long id,
            String domainPattern,
            String siteCode,
            String name,
            String description,
            String createdBy,
            String createdAt,
            String updatedBy,
            String updatedAt
    ) {
        // 엔티티 -> DTO 변환을 위한 정적 팩토리 메서드
        public static Response from(Tenant entity) {
            return new Response(
                    entity.getId(),
                    entity.getDomainPattern(),
                    entity.getSiteCode(),
                    entity.getName(),
                    entity.getDescription(),
                    entity.getCreatedBy(),
                    entity.getCreatedAt().toString(),
                    entity.getUpdatedBy(),
                    entity.getUpdatedAt().toString()
            );
        }
    }
}