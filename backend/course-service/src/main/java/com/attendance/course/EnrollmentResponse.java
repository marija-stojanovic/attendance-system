package com.attendance.course;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EnrollmentResponse(UUID id, StudentResponse student, UUID courseId, UUID offeringId,
                                 String academicYear, String status, List<UUID> groupIds, Instant enrolledAt) {
    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(enrollment.getId(), StudentResponse.from(enrollment.getStudent()),
                enrollment.getOffering().getCourse().getId(), enrollment.getOffering().getId(),
                enrollment.getOffering().getAcademicYear(), enrollment.getStatus(),
                enrollment.getGroups().stream().map(TeachingGroup::getId).sorted().toList(), enrollment.getEnrolledAt());
    }
}
