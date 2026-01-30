package com.mingchico.cms.core.xss;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 어노테이션이 붙은 필드는 XSS 정제 대상에서 제외됩니다.
 * CMS의 본문(content) 등 HTML 태그 유지가 필요한 DTO 필드에 사용합니다.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowHtml {
}