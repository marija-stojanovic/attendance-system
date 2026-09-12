package com.attendance.course;

import java.util.UUID;

public record OfferingResponse(UUID id, UUID courseId, String academicYear, String studyProgram,
                               String studyType, short yearOfStudy, short semester) {
    public static OfferingResponse from(CourseOffering offering) {
        return new OfferingResponse(offering.getId(), offering.getCourse().getId(), offering.getAcademicYear(),
                offering.getStudyProgram(), offering.getStudyType(), offering.getYearOfStudy(), offering.getSemester());
    }
}
