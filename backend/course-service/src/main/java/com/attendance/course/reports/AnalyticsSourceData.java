package com.attendance.course.reports;

import com.attendance.course.ActivityType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record AnalyticsSourceData(List<Enrollment> enrollments, List<Session> sessions,
                                  List<Attendance> attendances) {
    public AnalyticsSourceData {
        enrollments = List.copyOf(enrollments);
        sessions = List.copyOf(sessions);
        attendances = List.copyOf(attendances);
    }

    public record Student(UUID id, String firstName, String lastName, String indexNumber) {}

    public record Enrollment(Student student, List<UUID> groupIds) {
        public Enrollment { groupIds = List.copyOf(groupIds); }
    }

    public record Session(UUID id, LocalDate date, LocalTime startTime, ActivityType activityType,
                          UUID groupId, String groupNumber) {}

    public record Attendance(UUID sessionId, UUID studentId) {}
}
