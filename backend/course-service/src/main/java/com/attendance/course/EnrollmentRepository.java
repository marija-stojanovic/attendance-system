package com.attendance.course;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Enrollment> findByStudentIdAndOfferingId(UUID studentId, UUID offeringId);
    boolean existsByStudentIdAndOfferingId(UUID studentId, UUID offeringId);

    List<Enrollment> findByOfferingIdOrderByStudentLastNameAscStudentFirstNameAscStudentIndexNumberAsc(UUID offeringId);

    List<Enrollment> findByOfferingCourseIdOrderByStudentLastNameAscStudentFirstNameAscStudentIndexNumberAsc(UUID courseId);

    List<Enrollment> findByGroupsIdOrderByStudentLastNameAscStudentFirstNameAscStudentIndexNumberAsc(UUID groupId);

    @Query("""
            select distinct e from Enrollment e
            join fetch e.offering o
            join fetch o.course
            left join fetch e.groups
            where e.student.id = :studentId
            order by o.academicYear desc, o.course.name asc
            """)
    List<Enrollment> findCoursesForStudent(@Param("studentId") UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Enrollment e where e.id = :id")
    Optional<Enrollment> findForUpdate(@Param("id") UUID id);
}
