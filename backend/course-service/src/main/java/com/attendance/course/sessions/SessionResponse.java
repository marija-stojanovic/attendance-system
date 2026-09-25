package com.attendance.course.sessions;

import com.attendance.course.ActivityType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record SessionResponse(UUID id, UUID courseId, UUID offeringId, UUID groupId,
        String groupNumber, ActivityType activityType, LocalDate date, LocalTime startTime,
        SessionStatus status, Instant createdAt, int qrRotationSeconds) {
    public static SessionResponse from(TeachingSession session) {
        return new SessionResponse(session.getId(), session.getCourse().getId(),
                session.getGroup().getOffering().getId(), session.getGroup().getId(),
                session.getGroup().getGroupNumber(), session.getActivityType(), session.getDate(),
                session.getStartTime(), session.getStatus(), session.getCreatedAt(), session.getQrRotationSeconds());
    }
}
