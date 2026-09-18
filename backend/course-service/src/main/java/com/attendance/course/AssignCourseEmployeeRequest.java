package com.attendance.course;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssignCourseEmployeeRequest(
        @NotBlank @Email String email,
        @NotNull CourseEmployeeRole role) {
    public AssignCourseEmployeeRequest {
        email = email == null ? null : email.strip().toLowerCase();
    }
}
