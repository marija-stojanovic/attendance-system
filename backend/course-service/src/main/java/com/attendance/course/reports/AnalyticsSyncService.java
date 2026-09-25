package com.attendance.course.reports;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

@Service
public class AnalyticsSyncService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsSyncService.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AnalyticsSourceService sources;
    private final RestClient analytics;
    private final String internalKey;

    public AnalyticsSyncService(JdbcTemplate jdbc, PlatformTransactionManager manager,
            AnalyticsSourceService sources, @Value("${analytics-service.base-url}") String baseUrl,
            @Value("${analytics-service.internal-key}") String internalKey) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setIsolationLevel(TransactionTemplate.ISOLATION_REPEATABLE_READ);
        this.sources = sources;
        this.internalKey = internalKey;
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(3000);
        requests.setReadTimeout(10000);
        this.analytics = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }

    @Scheduled(fixedDelayString = "${analytics-service.sync-interval-ms:2000}")
    public void sync() {
        for (int count = 0; count < 20; count++) {
            try {
                if (!Boolean.TRUE.equals(transactions.execute(status -> sendNext()))) return;
            } catch (RuntimeException error) {
                log.warn("Osvježavanje analitike nije uspjelo; pokušaj će se ponoviti.", error);
                return;
            }
        }
    }

    private boolean sendNext() {
        var pending = jdbc.query("""
                SELECT offering_id FROM analytics_sync_queue
                ORDER BY changed_at, offering_id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (result, row) -> result.getObject("offering_id", UUID.class));
        if (pending.isEmpty()) return false;
        UUID offeringId = pending.getFirst();
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM course_offerings WHERE id = ?)",
                Boolean.class, offeringId);
        if (Boolean.TRUE.equals(exists)) {
            analytics.put().uri("/internal/analytics/offerings/{id}", offeringId)
                    .header("X-Internal-Key", internalKey)
                    .body(sources.loadInternal(offeringId))
                    .retrieve().toBodilessEntity();
        } else {
            analytics.delete().uri("/internal/analytics/offerings/{id}", offeringId)
                    .header("X-Internal-Key", internalKey)
                    .retrieve().toBodilessEntity();
        }
        jdbc.update("DELETE FROM analytics_sync_queue WHERE offering_id = ?", offeringId);
        return true;
    }
}
