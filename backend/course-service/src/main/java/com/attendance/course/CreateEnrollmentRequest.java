package com.attendance.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record CreateEnrollmentRequest(
        @NotNull UUID studentId,
        @NotBlank(message = "Status upisa je obavezan.") @Size(max = 30) String status,
        Set<@NotNull UUID> groupIds) {
    public CreateEnrollmentRequest {
        status = status == null ? null : status.strip();
        groupIds = groupIds == null ? Set.of() : groupIds;
    }
}
