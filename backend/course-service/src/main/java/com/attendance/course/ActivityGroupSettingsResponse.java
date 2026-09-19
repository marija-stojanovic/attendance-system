package com.attendance.course;

import java.util.List;

public record ActivityGroupSettingsResponse(ActivityType activityType, int studentCount,
        boolean configured, boolean selectionEnabled, List<ManagedGroupResponse> groups) {
}
