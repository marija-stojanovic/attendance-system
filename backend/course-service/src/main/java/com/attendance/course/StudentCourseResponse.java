package com.attendance.course;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record StudentCourseResponse(UUID enrollmentId, UUID courseId, String courseCode, String courseName,
        UUID offeringId, String academicYear, String studyProgram, String studyType,
        short yearOfStudy, short semester, String status, List<GroupInfo> groups) {

    public static StudentCourseResponse from(Enrollment enrollment) {
        CourseOffering offering = enrollment.getOffering();
        Course course = offering.getCourse();
        List<GroupInfo> assignedGroups = enrollment.getGroups().stream()
                .sorted(Comparator.comparing(TeachingGroup::getActivityType)
                        .thenComparing(TeachingGroup::getGroupNumber))
                .map(group -> new GroupInfo(group.getActivityType(), group.getGroupNumber()))
                .toList();
        return new StudentCourseResponse(enrollment.getId(), course.getId(), course.getCode(), course.getName(),
                offering.getId(), offering.getAcademicYear(), offering.getStudyProgram(), offering.getStudyType(),
                offering.getYearOfStudy(), offering.getSemester(), enrollment.getStatus(), assignedGroups);
    }

    public record GroupInfo(ActivityType activityType, String groupNumber) {
    }
}
