package com.mingchico.cms.core.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * <h3>[성별 코드]</h3>
 * <p>
 * 사용자의 성별 정보를 정의하는 열거형입니다.
 * </p>
 */
@Getter
@RequiredArgsConstructor
public enum Gender {
    
    MALE("남성"),
    FEMALE("여성"),
    OTHER("기타"),
    UNKNOWN("선택 안 함");

    private final String label;
}