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
public class AttendanceSummaryController {
    private final AttendanceSummaryService summaries;

    public AttendanceSummaryController(AttendanceSummaryService summaries) {
        this.summaries = summaries;
    }

    @GetMapping("/{offeringId}/attendance-summary")
    public ReportData.Summary summary(@PathVariable UUID offeringId, @AuthenticationPrincipal Jwt jwt) {
        return summaries.summary(offeringId, UUID.fromString(jwt.getSubject()));
    }
}
