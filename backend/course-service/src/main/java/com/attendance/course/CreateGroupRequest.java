package com.attendance.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(@NotNull ActivityType activityType, @NotBlank @Size(max = 20) String groupNumber) {
    public CreateGroupRequest {
        groupNumber = groupNumber == null ? null : groupNumber.strip();
    }
}
