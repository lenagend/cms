package com.mingchico.cms.core.tenant;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mingchico.cms.core.tenant.domain.Tenant;
import com.mingchico.cms.core.tenant.event.TenantRouteChangedEvent;
import com.mingchico.cms.core.tenant.repository.TenantRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * <h3>[도메인 테넌트 리졸버]</h3>
 * <p>
 * HTTP 요청의 도메인(Host)을 분석하여 매핑된 사이트 코드(Site Code)를 찾아냅니다.
 * 이벤트 리스너(Event Listener) 패턴을 도입하여, 관리자가 도메인을 수정했을 때만
 * 스마트하게 캐시를 갱신하도록 설계되었습니다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainTenantResolver implements TenantResolver {

    private final TenantRepository tenantRepository;

    // Ant 스타일 패턴 매칭 유틸리티 (예: *.shop.com)
    private final AntPathMatcher pathMatcher = new AntPathMatcher(".");

    // [Layer 1] Rule Cache: 매핑 규칙 원본 (순서 중요: LinkedHashMap 권장되나 로직상 List/Map 분리 관리)
    // 읽기 효율을 위해 "매칭 우선순위가 정렬된 키 목록"을 따로 관리
    private final Map<String, String> cachedRules = new ConcurrentHashMap<>();
    private final List<String> sortedPatterns = new ArrayList<>(); // 정렬된 키 목록 (매칭 순서 보장용)

    // [Layer 2] Result Cache: 요청 도메인별 계산 결과 캐시 (Caffeine)
    private final Cache<String, String> resolvedResultCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    /**
     * <h3>[이벤트 리스너: 도메인 변경 감지]</h3>
     * <p>
     * DB 트랜잭션이 성공적으로 커밋(Commit)된 후에만 캐시를 갱신합니다.
     * 이를 통해 DB에는 없는데 캐시만 갱신되는 '유령 데이터' 문제를 방지합니다.
     * </p>
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTenantRouteChanged(TenantRouteChangedEvent event) {
        log.info("📢 Tenant Route Change Committed. Refreshing rules now...");
        refreshRules();
    }

    /**
     * <h3>[규칙 동기화]</h3>
     * <p>
     * DB에서 모든 테넌트 정보를 가져와 메모리에 캐싱합니다.
     * 이때, <b>"구체적인 패턴"이 먼저 매칭되도록 정밀하게 정렬</b>합니다.
     * </p>
     */
    @PostConstruct
    @Scheduled(fixedDelay = 60000)
    public synchronized void refreshRules() {
        try {
            log.debug("Refreshing tenant rules from DB...");

            // 1. DB 조회 (정렬은 Java에서 수행)
            List<Tenant> allTenants = new ArrayList<>(tenantRepository.findAll());

            // 2. 스마트 정렬 로직 (Priority Sorting)
            // 우선순위 1: 와일드카드(*)가 없는 정확한 도메인 (admin.shop.com)
            // 우선순위 2: 와일드카드가 있어도 길이가 긴 패턴 (*.shop.com > *.com)
            allTenants.sort(Comparator.comparing((Tenant t) -> t.getDomainPattern().contains("*")) // false(0) -> true(1)
                    .thenComparing(t -> t.getDomainPattern().length(), Comparator.reverseOrder())); // 길이 긴 순

            // 3. 캐시 갱신 (Map & List)
            Map<String, String> newRules = new ConcurrentHashMap<>();
            List<String> newSortedKeys = new ArrayList<>();

            for (Tenant t : allTenants) {
                newRules.put(t.getDomainPattern(), t.getSiteCode());
                newSortedKeys.add(t.getDomainPattern());
            }

            // Atomic 교체에 가깝게 참조 변경
            cachedRules.clear();
            cachedRules.putAll(newRules);

            sortedPatterns.clear();
            sortedPatterns.addAll(newSortedKeys);

            // 4. 결과 캐시 초기화 (규칙 변경으로 인한 구형 데이터 제거)
            resolvedResultCache.invalidateAll();

            log.debug("✅ Tenant Rules Refreshed. Total Rules: {}", cachedRules.size());

        } catch (Exception e) {
            log.error("❌ Failed to refresh tenant rules from DB. Using cached rules.", e);
        }
    }

    @Override
    public String resolveSiteCode(HttpServletRequest request) {
        String serverName = request.getServerName().toLowerCase();

        // 1. [개발자용] 헤더 오버라이드
        String headerOverride = request.getHeader("X-Tenant-ID");
        if (StringUtils.hasText(headerOverride)) {
            return headerOverride;
        }

        // 2. [캐시 조회] Caffeine Cache
        String siteCode = resolvedResultCache.get(serverName, this::computeSiteCode);

        // 3. [최종 검증]
        if (siteCode == null) {
            throw new UnknownTenantException("등록되지 않은 도메인입니다: " + serverName);
        }

        return siteCode;
    }

    /**
     * <h3>[내부 연산 로직]</h3>
     * 정렬된 패턴 목록(sortedPatterns)을 순서대로 대조하여
     * 가장 구체적인 규칙(Best Match)을 찾아냅니다.
     */
    private String computeSiteCode(String domain) {
        // Step 1: 정확한 일치 (Exact Match) - O(1)
        if (cachedRules.containsKey(domain)) {
            return cachedRules.get(domain);
        }

        // Step 2: 정렬된 패턴 순차 검사 (Wildcard Match)
        // sortedPatterns는 이미 [구체적 -> 일반적] 순서로 정렬되어 있음
        for (String pattern : sortedPatterns) {
            if (pattern.contains("*") && pathMatcher.match(pattern, domain)) {
                log.debug("Wildcard Matched: Domain[{}] matches Pattern[{}]", domain, pattern);
                return cachedRules.get(pattern);
            }
        }

        return null;
    }
}