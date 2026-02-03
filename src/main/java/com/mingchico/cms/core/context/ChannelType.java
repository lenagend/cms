package com.mingchico.cms.core.context;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * [시스템 진입 채널 타입]
 */
@Getter
@RequiredArgsConstructor
public enum ChannelType {
    ADMIN_API("관리자 전용 API"),
    API("공개/사용자 API"),
    ADMIN("관리자 웹 화면"),
    WEB("사용자 웹 화면"),
    UNKNOWN("알 수 없는 채널");

    private final String description;
}