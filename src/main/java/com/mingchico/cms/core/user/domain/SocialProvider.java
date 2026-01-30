package com.mingchico.cms.core.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SocialProvider {
    GOOGLE("구글"),
    KAKAO("카카오"),
    NAVER("네이버"),
    APPLE("애플"),
    FACEBOOK("페이스북");

    private final String description;
}