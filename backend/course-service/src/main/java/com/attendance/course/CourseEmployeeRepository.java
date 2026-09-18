package com.attendance.course;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseEmployeeRepository extends JpaRepository<CourseEmployee, UUID> {
    boolean existsByCourseIdAndEmployeeId(UUID courseId, UUID employeeId);
    List<CourseEmployee> findByCourseIdOrderByEmployeeEmail(UUID courseId);

    @Query("select assignment.course from CourseEmployee assignment where assignment.employee.id = :employeeId order by assignment.course.code")
    List<Course> findCoursesByEmployeeId(@Param("employeeId") UUID employeeId);
}
