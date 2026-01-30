package com.mingchico.cms.core.xss;

import com.mingchico.cms.core.xss.AllowHtml;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * XSS 방어 전체 흐름을 검증하기 위한 테스트 전용 컨트롤러
 *
 * ✔ JSON Body
 * ✔ Query Parameter
 * ✔ Form Data
 * ✔ @AllowHtml 적용 케이스
 */
@RestController
@RequestMapping("/test/xss")
public class TestXssController {

    /**
     * JSON Body 기반 테스트
     */
    @PostMapping("/json")
    public Map<String, String> json(@RequestBody TestXssDto dto) {
        return Map.of(
                "title", dto.getTitle(),
                "content", dto.getContent()
        );
    }

    /**
     * Query Parameter 기반 테스트
     */
    @GetMapping("/query")
    public String query(@RequestParam String q) {
        return q;
    }

    /**
     * Form 데이터 기반 테스트
     */
    @PostMapping("/form")
    public String form(@RequestParam String value) {
        return value;
    }

    /**
     * @AllowHtml 직접 적용 테스트
     */
    @PostMapping("/allow")
    public String allow(@RequestBody AllowHtmlDto dto) {
        return dto.getContent();
    }

    /**
     * AllowHtml 테스트용 내부 DTO
     */
    public static class AllowHtmlDto {
        @AllowHtml
        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
