package com.attendance.course.attendance;

import java.time.Instant;
import java.util.UUID;

public record AttendanceResponse(UUID id, UUID sessionId, UUID studentId, String courseCode, String courseName,
                                 String activityType, String groupNumber, Instant recordedAt,
                                 String ipAddress, String userAgent) {
    static AttendanceResponse from(Attendance attendance) {
        var session = attendance.getSession();
        return new AttendanceResponse(attendance.getId(), session.getId(), attendance.getStudent().getId(),
                session.getCourse().getCode(), session.getCourse().getName(), session.getActivityType().name(),
                session.getGroup().getGroupNumber(), attendance.getRecordedAt(),
                attendance.getIpAddress(), attendance.getUserAgent());
    }
}
