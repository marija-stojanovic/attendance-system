package com.attendance.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateEnrollmentStatusRequest(@NotBlank @Size(max = 30) String status) {
    public UpdateEnrollmentStatusRequest {
        status = status == null ? null : status.strip();
    }
}
