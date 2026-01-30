package com.mingchico.cms.core.tenant.controller;

import com.mingchico.cms.core.tenant.dto.TenantDto;
import com.mingchico.cms.core.tenant.service.TenantAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <h3>[테넌트 관리자 API]</h3>
 * <p>
 * 사이트와 도메인 매핑을 관리하는 API입니다.
 * 보안상 <b>관리자(ADMIN) 권한</b>을 가진 사용자만 접근 가능해야 합니다.
 * </p>
 */
@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
// [보안] 클래스 레벨에서 ADMIN 권한 강제 (메서드별 실수 방지)
@PreAuthorize("hasRole('ADMIN')")
public class TenantAdminController {

    private final TenantAdminService tenantService;

    @GetMapping
    public ResponseEntity<List<TenantDto.Response>> getTenants() {
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    @PostMapping
    public ResponseEntity<TenantDto.Response> createTenant(@RequestBody @Valid TenantDto.CreateRequest request) {
        TenantDto.Response response = tenantService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantDto.Response> updateTenant(
            @PathVariable Long id,
            @RequestBody @Valid TenantDto.UpdateRequest request) {
        return ResponseEntity.ok(tenantService.updateTenant(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTenant(@PathVariable Long id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }
}