package com.attendance.analytics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/analytics/offerings")
public class AnalyticsSyncController {
    private final AnalyticsSnapshotRepository snapshots;
    private final byte[] internalKey;

    public AnalyticsSyncController(AnalyticsSnapshotRepository snapshots,
            @Value("${analytics-service.internal-key}") String internalKey) {
        this.snapshots = snapshots;
        this.internalKey = internalKey.getBytes(StandardCharsets.UTF_8);
    }

    @PutMapping("/{offeringId}")
    public void update(@PathVariable UUID offeringId, @RequestHeader("X-Internal-Key") String suppliedKey,
            @RequestBody AnalyticsData.Source source) {
        requireKey(suppliedKey);
        if (source.enrollments() == null || source.sessions() == null || source.attendances() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaci predmeta nisu potpuni.");
        }
        snapshots.save(offeringId, source);
    }

    @DeleteMapping("/{offeringId}")
    public void delete(@PathVariable UUID offeringId, @RequestHeader("X-Internal-Key") String suppliedKey) {
        requireKey(suppliedKey);
        snapshots.delete(offeringId);
    }

    private void requireKey(String suppliedKey) {
        if (!MessageDigest.isEqual(internalKey, suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
