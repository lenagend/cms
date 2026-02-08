package com.mingchico.cms.core.menu.dto;

import com.mingchico.cms.core.menu.domain.Menu;
import com.mingchico.cms.core.menu.domain.MenuConfig;
import com.mingchico.cms.core.menu.domain.MenuTarget;
import com.mingchico.cms.core.menu.domain.MenuType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <h3>[메뉴 관리 DTO]</h3>
 */
public class MenuDto {

    /**
     * [등록/수정 요청 DTO]
     */
    @Builder
    public record SaveRequest(
            @NotBlank(message = "사이트 코드는 필수입니다.")
            String siteCode,

            Long parentId,

            @NotBlank(message = "메뉴명은 필수입니다.")
            String name,

            @NotBlank(message = "URL 패턴은 필수입니다.")
            String urlPattern,

            @NotNull(message = "메뉴 유형은 필수입니다.")
            MenuType type,

            String handler,
            MenuTarget target,
            String icon,
            int displayOrder,

            boolean visible,
            boolean accessible,

            String readRoles,  // CSV 형태 (예: "ANONYMOUS,ROLE_USER")
            String writeRoles,  // CSV 형태 (예: "ROLE_ADMIN"),
            MenuConfig config
    ) {}

    /**
     * [상세 응답 DTO]
     * 계층형 트리 구조를 지원합니다.
     */
    public record Response(
            Long id,
            Long parentId,
            String name,
            String urlPattern,
            MenuType type,
            String handler,
            MenuTarget target,
            String icon,
            int displayOrder,
            boolean visible,
            boolean accessible,
            List<String> readRoles,
            List<String> writeRoles,
            MenuConfig config,
            List<Response> children // 재귀적 트리 구조
    ) {
        public static Response from(Menu entity) {
            return new Response(
                    entity.getId(),
                    entity.getParentId(),
                    entity.getName(),
                    entity.getUrlPattern(),
                    entity.getType(),
                    entity.getHandler(),
                    entity.getTarget(),
                    entity.getIcon(),
                    entity.getDisplayOrder(),
                    entity.isVisible(),
                    entity.isAccessible(),
                    entity.getReadRoleSet().stream().toList(),
                    entity.getWriteRoleSet().stream().toList(),
                    entity.getConfig() != null ? entity.getConfig() : new MenuConfig(),
                    entity.getChildren().stream()
                            .map(Response::from)
                            .collect(Collectors.toList())
            );
        }
    }
}