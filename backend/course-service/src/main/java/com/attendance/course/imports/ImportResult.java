package com.attendance.course.imports;

import java.util.List;
import java.util.UUID;

public record ImportResult(UUID courseId, UUID offeringId, UUID groupId, List<UUID> groupIds,
                           boolean courseCreated, boolean offeringCreated, boolean groupCreated, int groupsCreated,
                           int studentsCreated,
                           int studentsReused, int enrollmentsCreated, int enrollmentsReused,
                           int groupMembershipsAdded, List<ImportPreview.RowWarning> warnings) {
}
