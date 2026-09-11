package com.attendance.course;

import java.time.Instant;
import java.util.UUID;

public record CourseResponse(UUID id, String code, String name, Instant createdAt) {
    public static CourseResponse from(Course course) {
        return new CourseResponse(course.getId(), course.getCode(), course.getName(), course.getCreatedAt());
    }
}
