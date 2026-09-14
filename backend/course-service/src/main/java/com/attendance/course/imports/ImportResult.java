package com.attendance.course.imports;

import java.util.UUID;

public record ImportResult(UUID courseId, UUID offeringId, UUID groupId, boolean courseCreated,
                           boolean offeringCreated, boolean groupCreated, int studentsCreated,
                           int studentsReused, int enrollmentsCreated, int enrollmentsReused,
                           int groupMembershipsAdded) {
}
