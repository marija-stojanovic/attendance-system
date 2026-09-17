package com.attendance.course.sessions;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record SessionRequest(
        @NotNull(message = "Grupa je obavezna.") UUID groupId,
        @NotNull(message = "Datum je obavezan.") LocalDate date,
        @NotNull(message = "Vrijeme početka je obavezno.") LocalTime startTime) {
}
