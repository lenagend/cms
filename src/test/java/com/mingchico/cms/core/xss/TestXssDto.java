package com.mingchico.cms.core.xss;

import com.mingchico.cms.core.xss.AllowHtml;
import lombok.Getter;
import lombok.Setter;

/**
 * CMS 게시글 DTO 시뮬레이션
 * title  → HTML 불가
 * content → HTML 허용
 */
@Setter
@Getter
public class TestXssDto {

    private String title;

    @AllowHtml
    private String content;

}
