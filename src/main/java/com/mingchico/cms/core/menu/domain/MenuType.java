package com.mingchico.cms.core.menu.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MenuType {
    PAGE("일반 페이지", "content/view"),   // CMS가 직접 관리하는 정적/동적 페이지
    BOARD("게시판", "board/list"),        // 공지사항, 자유게시판 등 게시판 모듈
    LINK("외부 링크", ""),               // 다른 사이트로 리다이렉트 (View 없음)
    GROUP("그룹 헤더", "");              // 클릭 불가능한 단순 분류용 폴더

    private final String description;
    private final String defaultViewPath; // 리졸버가 뷰를 찾을 때 참고할 기본 경로
}