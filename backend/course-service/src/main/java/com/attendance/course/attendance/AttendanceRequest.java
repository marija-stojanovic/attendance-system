package com.attendance.course.attendance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AttendanceRequest(
        @NotBlank(message = "QR token je obavezan.")
        @Size(max = 2048, message = "QR token nije ispravan.")
        String token) {
}
