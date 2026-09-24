package com.attendance.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsCalculatorTests {
    @Test
    void countsOnlyApplicableSessionsAndIncludesStudentsWithoutAttendance() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        var first = new AnalyticsData.Student(firstId, "Ana", "Anić", "101/25");
        var second = new AnalyticsData.Student(secondId, "Bojan", "Babić", "102/25");
        var date = LocalDate.of(2026, 10, 1);
        var source = new AnalyticsData.Source(
                List.of(new AnalyticsData.Enrollment(first, List.of(groupId)),
                        new AnalyticsData.Enrollment(second, List.of())),
                List.of(new AnalyticsData.Session(lectureId, date, LocalTime.of(9, 0),
                                "LECTURE", UUID.randomUUID(), "ALL"),
                        new AnalyticsData.Session(exerciseId, date, LocalTime.of(11, 0),
                                "AUDITORY_EXERCISE", groupId, "G1")),
                List.of(new AnalyticsData.Attendance(lectureId, firstId),
                        new AnalyticsData.Attendance(exerciseId, firstId)));

        var result = new AnalyticsCalculator(BigDecimal.valueOf(75)).calculate(source);

        assertThat(result.closedSessions()).isEqualTo(2);
        assertThat(result.students()).extracting(AnalyticsData.StudentSummary::arrivals)
                .containsExactly(2, 0);
        assertThat(result.students()).extracting(AnalyticsData.StudentSummary::expectedSessions)
                .containsExactly(2, 1);
        assertThat(result.lowAttendanceStudents()).extracting(item -> item.student().id())
                .containsExactly(secondId);
        assertThat(result.sessions()).extracting(AnalyticsData.SessionSummary::expectedStudents)
                .containsExactly(2, 1);
        assertThat(result.attendanceTrend().getFirst().attendancePercent())
                .isEqualByComparingTo("66.67");
        assertThat(result.groupComparisons()).hasSize(1);
        assertThat(result.groupComparisons().getFirst().attendancePercent())
                .isEqualByComparingTo("100.00");
    }
}
