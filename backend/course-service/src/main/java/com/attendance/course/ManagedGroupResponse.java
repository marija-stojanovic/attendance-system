package com.attendance.course;

import java.util.UUID;

public record ManagedGroupResponse(UUID id, String groupNumber, int capacity, long occupied) {
}
