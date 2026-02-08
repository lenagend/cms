package com.mingchico.cms.core.menu.domain;

import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;

@Getter
@Setter
public class MenuConfig implements Serializable {
    // TODO: 기능별 추가설정
    // --- [Board Specific] 게시판 전용 설정 ---
    private boolean commentEnabled = true;     // 댓글 허용 여부
    private boolean fileUploadEnabled = true;  // 파일 첨부 허용 여부
    private boolean secretEnabled = false;     // 비밀글 기능 사용 여부
    private int pageSize = 20;                 // 페이지당 게시물 수
    
    // --- [Page Specific] 페이지 전용 설정 ---
    private boolean showSidebar = true;        // 사이드바 노출 여부
    private String headerImage = "";           // 페이지별 헤더 이미지
}