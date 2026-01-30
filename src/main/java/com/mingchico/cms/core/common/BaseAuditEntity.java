package com.mingchico.cms.core.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * <h3>[공통 감사 엔티티 (Base Audit Entity)]</h3>
 * <p>
 * 모든 엔티티의 상위 클래스로 사용하여, 다음 정보를 자동으로 관리합니다.
 * 1. 생성일시 (createdAt)
 * 2. 수정일시 (updatedAt)
 * 3. 생성자 (createdBy)
 * 4. 수정자 (updatedBy)
 * </p>
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditEntity {

    // --- [시간 정보] ---
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // --- [작성자 정보] ---
    // 로그인 ID(String) 또는 회원 PK(Long)를 저장할 수 있습니다.
    // CMS 특성상 관리자 ID를 직관적으로 보기 위해 String을 추천합니다.
    
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;
}