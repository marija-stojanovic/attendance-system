package com.attendance.course.sessions;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record SessionRequest(
        @NotNull(message = "Grupa je obavezna.") UUID groupId,
        @NotNull(message = "Datum je obavezan.") LocalDate date,
        @NotNull(message = "Vrijeme početka je obavezno.") LocalTime startTime,
        @Min(value = 5, message = "Interval QR koda mora biti najmanje 5 sekundi.")
        @Max(value = 300, message = "Interval QR koda može biti najviše 300 sekundi.")
        Integer qrRotationSeconds) {
    public SessionRequest {
        if (qrRotationSeconds == null) qrRotationSeconds = 30;
    }
}
