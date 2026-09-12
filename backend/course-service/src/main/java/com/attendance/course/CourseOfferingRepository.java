package com.attendance.course;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, UUID> {
    List<CourseOffering> findByCourseIdOrderByAcademicYearDescStudyProgramAscStudyTypeAsc(UUID courseId);
}
