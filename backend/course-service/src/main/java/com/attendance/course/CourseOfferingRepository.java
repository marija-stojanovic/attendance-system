package com.attendance.course;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, UUID> {
    java.util.Optional<CourseOffering> findByCourseIdAndAcademicYearAndStudyProgramAndStudyType(
            UUID courseId, String academicYear, String studyProgram, String studyType);
    List<CourseOffering> findByCourseIdOrderByAcademicYearDescStudyProgramAscStudyTypeAsc(UUID courseId);
}
