package com.attendance.course;

import java.util.List;
import java.util.UUID;

public record StudentGroupSelectionResponse(ActivityType activityType, boolean selectionEnabled,
        UUID currentGroupId, List<ManagedGroupResponse> groups) {
}
