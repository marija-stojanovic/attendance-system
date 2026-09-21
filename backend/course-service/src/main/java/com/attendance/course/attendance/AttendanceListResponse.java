package com.attendance.course.attendance;

import java.time.Instant;
import java.util.UUID;

public record AttendanceListResponse(UUID id, UUID studentId, String firstName, String lastName,
                                     String indexNumber, String groupNumber, Instant recordedAt,
                                     String ipAddress, String userAgent) {
    static AttendanceListResponse from(Attendance attendance) {
        var student = attendance.getStudent();
        return new AttendanceListResponse(attendance.getId(), student.getId(), student.getFirstName(),
                student.getLastName(), student.getIndexNumber(), attendance.getSession().getGroup().getGroupNumber(),
                attendance.getRecordedAt(), attendance.getIpAddress(), attendance.getUserAgent());
    }
}
