package com.attendance.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsCalculator {
    private final BigDecimal lowAttendanceThreshold;

    public AnalyticsCalculator(@Value("${attendance-analytics.low-attendance-threshold-percent:75}")
            BigDecimal lowAttendanceThreshold) {
        if (lowAttendanceThreshold.compareTo(BigDecimal.ZERO) < 0
                || lowAttendanceThreshold.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Prag prisustva mora biti između 0 i 100.");
        }
        this.lowAttendanceThreshold = lowAttendanceThreshold.setScale(2, RoundingMode.HALF_UP);
    }

    public AnalyticsData.Summary calculate(AnalyticsData.Source source) {
        var closedSessions = source.sessions().stream()
                .sorted(Comparator.comparing(AnalyticsData.Session::date)
                        .thenComparing(AnalyticsData.Session::startTime)
                        .thenComparing(AnalyticsData.Session::id)).toList();
        Map<UUID, Set<UUID>> sessionsByStudent = new HashMap<>();
        Map<UUID, Set<UUID>> studentsBySession = new HashMap<>();
        for (var attendance : source.attendances()) {
            sessionsByStudent.computeIfAbsent(attendance.studentId(), ignored -> new HashSet<>())
                    .add(attendance.sessionId());
            studentsBySession.computeIfAbsent(attendance.sessionId(), ignored -> new HashSet<>())
                    .add(attendance.studentId());
        }

        var students = source.enrollments().stream().map(enrollment -> {
            Set<UUID> attendedIds = sessionsByStudent.getOrDefault(enrollment.student().id(), Set.of());
            int expected = 0;
            int arrivals = 0;
            for (var session : closedSessions) {
                if (applies(enrollment, session, attendedIds)) {
                    expected++;
                    if (attendedIds.contains(session.id())) arrivals++;
                }
            }
            return new AnalyticsData.StudentSummary(enrollment.student(), arrivals, expected - arrivals,
                    expected, percentage(arrivals, expected));
        }).sorted(Comparator.comparingInt(AnalyticsData.StudentSummary::arrivals).reversed()
                .thenComparing(item -> item.student().lastName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> item.student().firstName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> item.student().indexNumber()))
                .toList();
        var lowAttendanceStudents = students.stream()
                .filter(student -> student.expectedSessions() > 0
                        && student.attendancePercent().compareTo(lowAttendanceThreshold) < 0)
                .toList();
        var sessionSummaries = closedSessions.stream().map(session -> {
            Set<UUID> presentStudentIds = studentsBySession.getOrDefault(session.id(), Set.of());
            int expectedStudents = (int) source.enrollments().stream()
                    .filter(enrollment -> applies(enrollment, session,
                            sessionsByStudent.getOrDefault(enrollment.student().id(), Set.of())))
                    .count();
            return new AnalyticsData.SessionSummary(session.id(), session.date(), session.startTime(),
                    session.activityType(), session.groupNumber(), expectedStudents,
                    presentStudentIds.size(), percentage(presentStudentIds.size(), expectedStudents));
        }).toList();
        Map<LocalDate, Totals> trendByDate = new TreeMap<>();
        for (var session : sessionSummaries) {
            trendByDate.computeIfAbsent(session.date(), ignored -> new Totals())
                    .add(session.expectedStudents(), session.presentStudents());
        }
        var attendanceTrend = trendByDate.entrySet().stream()
                .map(entry -> new AnalyticsData.TrendPoint(entry.getKey(), entry.getValue().sessions,
                        entry.getValue().expected, entry.getValue().recorded,
                        percentage(entry.getValue().recorded, entry.getValue().expected)))
                .toList();
        Map<AnalyticsData.ActivityGroup, Totals> totalsByGroup = new LinkedHashMap<>();
        for (var session : sessionSummaries) {
            if ("LECTURE".equals(session.activityType())) continue;
            var group = new AnalyticsData.ActivityGroup(session.activityType(), session.groupNumber());
            totalsByGroup.computeIfAbsent(group, ignored -> new Totals())
                    .add(session.expectedStudents(), session.presentStudents());
        }
        var groupComparisons = totalsByGroup.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(AnalyticsData.ActivityGroup::activityType)
                        .thenComparing(AnalyticsData.ActivityGroup::groupNumber)))
                .map(entry -> new AnalyticsData.GroupComparison(entry.getKey().activityType(),
                        entry.getKey().groupNumber(), entry.getValue().sessions, entry.getValue().students,
                        entry.getValue().expected, entry.getValue().recorded,
                        percentage(entry.getValue().recorded, entry.getValue().expected)))
                .toList();
        return new AnalyticsData.Summary(closedSessions.size(), lowAttendanceThreshold,
                lowAttendanceStudents, sessionSummaries, attendanceTrend, groupComparisons, students);
    }

    private boolean applies(AnalyticsData.Enrollment enrollment, AnalyticsData.Session session,
            Set<UUID> attendedSessionIds) {
        return "LECTURE".equals(session.activityType())
                || enrollment.groupIds().contains(session.groupId())
                || attendedSessionIds.contains(session.id());
    }

    private BigDecimal percentage(int value, int total) {
        if (total == 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static final class Totals {
        private int sessions;
        private int students;
        private int expected;
        private int recorded;

        private void add(int expected, int recorded) {
            sessions++;
            students = Math.max(students, expected);
            this.expected += expected;
            this.recorded += recorded;
        }
    }
}
