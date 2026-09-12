package com.attendance.course;

import java.util.UUID;

public record GroupResponse(UUID id, UUID offeringId, ActivityType activityType, String groupNumber) {
    public static GroupResponse from(TeachingGroup group) {
        return new GroupResponse(group.getId(), group.getOffering().getId(), group.getActivityType(), group.getGroupNumber());
    }
}
