package com.mingchico.cms.core.tenant.dto;

import com.mingchico.cms.core.tenant.domain.Tenant;
import com.mingchico.cms.core.tenant.domain.TenantFeatures;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class TenantDto {

    /**
     * [테넌트 생성 요청]
     */
    public record CreateRequest(
            @NotBlank(message = "도메인 패턴은 필수입니다.")
            @Pattern(regexp = "^[a-zA-Z0-9.*-]+$", message = "도메인 형식이 올바르지 않습니다.")
            String domainPattern,

            @NotBlank(message = "사이트 코드는 필수입니다.")
            @Pattern(regexp = "^[A-Z0-9_]+$", message = "사이트 코드는 대문자와 언더바(_)만 가능합니다.")
            String siteCode,

            @NotBlank(message = "사이트 이름은 필수입니다.")
            String name,

            String description,

            // [Safety] 테마 코드 패턴 검증
            @Pattern(regexp = "^[a-z0-9_-]+$", message = "테마 코드는 소문자, 숫자, 하이픈, 언더바만 가능합니다.")
            String themeName,

            // 초기 기능 설정 (Optional)
            TenantFeatures features
    ) {}

    /**
     * [테넌트 수정 요청]
     */
    public record UpdateRequest(
            @NotBlank(message = "사이트 이름은 필수입니다.")
            String name,

            String description,

            @Pattern(regexp = "^[a-z0-9_-]+$", message = "테마 코드는 소문자, 숫자, 하이픈, 언더바만 가능합니다.")
            String themeName,

            // 기능 설정 변경
            TenantFeatures features
    ) {}

    /**
     * [응답 DTO]
     */
    public record Response(
            Long id,
            String domainPattern,
            String siteCode,
            String name,
            String description,
            String themeName,
            TenantFeatures features, // 프론트엔드에서 체크박스 렌더링용
            String createdBy,
            String createdAt,
            String updatedBy,
            String updatedAt
    ) {
        public static Response from(Tenant entity) {
            return new Response(
                    entity.getId(),
                    entity.getDomainPattern(),
                    entity.getSiteCode(),
                    entity.getName(),
                    entity.getDescription(),
                    entity.getThemeName(),
                    entity.getFeatures() != null ? entity.getFeatures() : new TenantFeatures(),
                    entity.getCreatedBy(),
                    entity.getCreatedAt().toString(),
                    entity.getUpdatedBy(),
                    entity.getUpdatedAt().toString()
            );
        }
    }
}