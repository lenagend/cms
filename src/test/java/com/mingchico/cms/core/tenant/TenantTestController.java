package com.mingchico.cms.core.tenant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통합 테스트에서 테넌트 주입 여부를 실제 HTTP 응답으로 확인하기 위한 컨트롤러입니다.
 * 실제 서비스 코드가 아닌 테스트 경로(src/test/java)에 위치시킵니다.
 */
@RestController
public class TenantTestController {
    
    @GetMapping("/test/tenant-check")
    public String check() {
        // TenantContext에 값이 정상적으로 박혔다면 해당 사이트 코드가 반환됨
        return TenantContext.getSiteCode();
    }
}