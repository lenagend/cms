package com.mingchico.cms.core.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {
    ACTIVE("정상 활성"),
    DORMANT("휴면 (장기 미접속)"),
    LOCKED("잠금 (비밀번호 오류 등)"),
    WITHDRAWN("탈퇴 (유예 기간)");

    private final String description;
}