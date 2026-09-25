package com.attendance.analytics;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics/offerings")
public class AnalyticsController {
    private final CourseDataClient courses;
    private final AnalyticsSnapshotRepository snapshots;
    private final AnalyticsCalculator calculator;

    public AnalyticsController(CourseDataClient courses, AnalyticsSnapshotRepository snapshots,
            AnalyticsCalculator calculator) {
        this.courses = courses;
        this.snapshots = snapshots;
        this.calculator = calculator;
    }

    @GetMapping("/{offeringId}/attendance-summary")
    public AnalyticsData.Summary summary(@PathVariable UUID offeringId,
            @AuthenticationPrincipal Jwt jwt) {
        courses.requireAccess(offeringId, jwt.getTokenValue());
        var source = snapshots.load(offeringId).orElseGet(() -> {
            var loaded = courses.load(offeringId, jwt.getTokenValue());
            snapshots.save(offeringId, loaded);
            return loaded;
        });
        return calculator.calculate(source);
    }
}
