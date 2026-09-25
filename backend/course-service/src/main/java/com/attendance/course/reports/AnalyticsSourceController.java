package com.attendance.course.reports;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/offerings")
public class AnalyticsSourceController {
    private final AnalyticsSourceService sources;
    private final AttendanceReportDataService reports;

    public AnalyticsSourceController(AnalyticsSourceService sources, AttendanceReportDataService reports) {
        this.sources = sources;
        this.reports = reports;
    }

    @GetMapping("/{offeringId}/analytics-source")
    public AnalyticsSourceData load(@PathVariable UUID offeringId, @AuthenticationPrincipal Jwt jwt) {
        return sources.load(offeringId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/{offeringId}/analytics-access")
    public void requireAccess(@PathVariable UUID offeringId, @AuthenticationPrincipal Jwt jwt) {
        reports.offering(offeringId, UUID.fromString(jwt.getSubject()));
    }
}
