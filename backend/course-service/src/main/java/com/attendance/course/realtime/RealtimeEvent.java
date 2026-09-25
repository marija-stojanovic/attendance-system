package com.attendance.course.realtime;

import java.util.UUID;

public record RealtimeEvent(String type, UUID courseId, UUID offeringId, UUID sessionId) {
}
