package com.attendance.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class AnalyticsData {
    private AnalyticsData() {}

    public record Student(UUID id, String firstName, String lastName, String indexNumber) {}
    public record Enrollment(Student student, List<UUID> groupIds) {}
    public record Session(UUID id, LocalDate date, LocalTime startTime, String activityType,
                          UUID groupId, String groupNumber) {}
    public record Attendance(UUID sessionId, UUID studentId) {}
    public record Source(List<Enrollment> enrollments, List<Session> sessions, List<Attendance> attendances) {}

    public record StudentSummary(Student student, int arrivals, int absences,
                                 int expectedSessions, BigDecimal attendancePercent) {}
    public record ActivityGroup(String activityType, String groupNumber) {}
    public record SessionSummary(UUID id, LocalDate date, LocalTime startTime, String activityType,
                                 String groupNumber, int expectedStudents, int presentStudents,
                                 BigDecimal attendancePercent) {}
    public record TrendPoint(LocalDate date, int closedSessions, int expectedAttendances,
                             int recordedAttendances, BigDecimal attendancePercent) {}
    public record GroupComparison(String activityType, String groupNumber, int closedSessions,
                                  int students, int expectedAttendances, int recordedAttendances,
                                  BigDecimal attendancePercent) {}
    public record Summary(int closedSessions, BigDecimal lowAttendanceThresholdPercent,
                          List<StudentSummary> lowAttendanceStudents, List<SessionSummary> sessions,
                          List<TrendPoint> attendanceTrend, List<GroupComparison> groupComparisons,
                          List<StudentSummary> students) {}
}
