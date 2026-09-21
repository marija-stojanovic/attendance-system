package com.attendance.course.attendance;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record AttendanceRequest(
        @Size(max = 2048, message = "QR token nije ispravan.")
        String token,
        @Pattern(regexp = "\\d{6}", message = "PIN mora imati šest cifara.")
        String pin,
        UUID requestId) {
}
