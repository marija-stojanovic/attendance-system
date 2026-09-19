package com.attendance.course;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record GroupSettingsRequest(@Min(1) @Max(100) int groupCount, @Min(1) @Max(1000) int groupCapacity,
        boolean selectionEnabled) {
}
