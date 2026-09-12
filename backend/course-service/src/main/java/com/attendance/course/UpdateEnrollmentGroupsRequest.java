package com.attendance.course;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record UpdateEnrollmentGroupsRequest(@NotNull Set<@NotNull UUID> groupIds) {
}
