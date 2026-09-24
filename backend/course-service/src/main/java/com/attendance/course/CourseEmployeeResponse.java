package com.attendance.course;

import java.util.UUID;

public record CourseEmployeeResponse(UUID id, UUID employeeId, String email, String username,
                                     CourseEmployeeRole role) {
    public static CourseEmployeeResponse from(CourseEmployee assignment) {
        return new CourseEmployeeResponse(assignment.getId(), assignment.getEmployeeId(),
                assignment.getEmployeeEmail(), assignment.getEmployeeUsername(), assignment.getRole());
    }
}
