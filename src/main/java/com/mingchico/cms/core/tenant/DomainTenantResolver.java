package com.mingchico.cms.core.tenant;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mingchico.cms.core.tenant.domain.Tenant;
import com.mingchico.cms.core.tenant.repository.TenantRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * <h3>[DB & Caffeine 기반 도메인 리졸버]</h3>
 * <p>
 * DB에서 관리되는 도메인 매핑 규칙을 기반으로 사이트 코드를 식별합니다.
 * 고성능 처리를 위해 <b>이중 캐싱(Rule Cache + Result Cache)</b> 전략을 사용합니다.
 * </p>
 *
 * <h3>아키텍처 설명</h3>
 * <ol>
 * <li><b>Rule Cache (Memory Map):</b> DB의 모든 매핑 규칙을 주기적으로 메모리에 로드합니다. DB 조회는 1분에 1회만 발생합니다.</li>
 * <li><b>Result Cache (Caffeine):</b> 요청된 도메인(예: shop.a.com)에 대해 연산된 결과(SITE_A)를 캐싱합니다. 두 번째 요청부터는 0ms에 가깝게 처리됩니다.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainTenantResolver implements TenantResolver {

    private final TenantRepository tenantRepository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher(".");

    // [1] Rule Cache: DB의 모든 규칙을 들고 있는 메모리 저장소
    // ConcurrentHashMap을 사용하여 읽기/쓰기 동시성 보장
    private final Map<String, String> cachedRules = new ConcurrentHashMap<>();

    // [2] Result Cache: "요청 도메인 -> 사이트 코드" 결과 캐시
    // - 최대 10,000개 도메인 결과 저장
    // - 마지막 접근 후 10분 지나면 만료 (규칙 변경 시 자연스럽게 갱신되도록)
    private final Cache<String, String> resolvedResultCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    /**
     * [초기화 및 주기적 갱신]
     * 서버 시작 시 실행되며, 이후 60초마다 DB에서 최신 규칙을 가져와 메모리를 갱신합니다.
     * 운영자가 DB를 바꿔도 최대 60초 내에 전 서버에 반영됩니다.
     */
    @PostConstruct
    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    public void refreshRules() {
        try {
            log.debug("Refreshing Tenant Rules from DB...");
            List<Tenant> mappings = tenantRepository.findAllByOrderByDomainPatternDesc();

            // 기존 규칙과 비교하여 변경이 있을 때만 로그 남기거나 교체 가능하지만,
            // 여기서는 단순함을 위해 전체 갱신 (Map putAll은 비용이 낮음)
            cachedRules.clear();
            for (Tenant mapping : mappings) {
                cachedRules.put(mapping.getDomainPattern(), mapping.getSiteCode());
            }

            // 규칙이 바뀌었을 수 있으므로, 기존에 계산해둔 결과 캐시(Result Cache)도 날려줍니다.
            // (즉시 반영을 위해)
            resolvedResultCache.invalidateAll();

            log.info("Tenant Rules Refreshed. Total Rules: {}", cachedRules.size());
        } catch (Exception e) {
            log.error("Failed to refresh tenant rules from DB", e);
            // DB 장애 시에는 기존에 메모리에 있는 cachedRules를 그대로 사용 (Fail-Safe)
        }
    }

    @Override
    public String resolveSiteCode(HttpServletRequest request) {
        String serverName = request.getServerName().toLowerCase();

        // 1. 개발자용 헤더 오버라이드 (최우선)
        String headerOverride = request.getHeader("X-Tenant-ID");
        if (StringUtils.hasText(headerOverride)) {
            return headerOverride;
        }

        // 2. Caffeine Cache 조회 (이미 계산된 도메인인가?)
        // get(key, mappingFunction)을 사용하여, 값이 없을 때만 내부 로직(computeSiteCode) 실행
        String siteCode = resolvedResultCache.get(serverName, this::computeSiteCode);

        if (siteCode == null) {
            throw new UnknownTenantException("등록되지 않은 도메인입니다: " + serverName);
        }

        return siteCode;
    }

    /**
     * [실제 계산 로직]
     * 캐시에 없을 때 실행되며, 메모리에 로드된 규칙(cachedRules)을 순회하며 매칭을 시도합니다.
     */
    private String computeSiteCode(String domain) {
        // 1. 정확한 일치 검색 (O(1))
        if (cachedRules.containsKey(domain)) {
            return cachedRules.get(domain);
        }

        // 2. 와일드카드 패턴 매칭 (O(N))
        // cachedRules는 DB에서 로드된 소수의 규칙(수백 개 이내)이므로 루프를 돌아도 매우 빠릅니다.
        for (Map.Entry<String, String> entry : cachedRules.entrySet()) {
            String pattern = entry.getKey();
            if (pattern.contains("*") && pathMatcher.match(pattern, domain)) {
                log.debug("Wildcard Matched: {} -> {} (Site: {})", domain, pattern, entry.getValue());
                return entry.getValue();
            }
        }

        // 3. 매칭 실패 -> null 반환 (Caffeine은 null을 저장하지 않으므로 호출 측에서 예외 처리)
        return null;
    }
}