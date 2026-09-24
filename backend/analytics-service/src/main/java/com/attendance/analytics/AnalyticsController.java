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
    private final AnalyticsCalculator calculator;

    public AnalyticsController(CourseDataClient courses, AnalyticsCalculator calculator) {
        this.courses = courses;
        this.calculator = calculator;
    }

    @GetMapping("/{offeringId}/attendance-summary")
    public AnalyticsData.Summary summary(@PathVariable UUID offeringId,
            @AuthenticationPrincipal Jwt jwt) {
        return calculator.calculate(courses.load(offeringId, jwt.getTokenValue()));
    }
}
