package com.attendance.course.attendance;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    boolean existsBySessionIdAndStudentId(UUID sessionId, UUID studentId);
    Optional<Attendance> findByRequestIdAndStudentId(UUID requestId, UUID studentId);

    @EntityGraph(attributePaths = "student")
    List<Attendance> findBySessionIdOrderByRecordedAtAscStudentLastNameAscStudentFirstNameAsc(UUID sessionId);
    Optional<Attendance> findByIdAndSessionId(UUID id, UUID sessionId);

    @Query("select a.session.id as sessionId, a.student.id as studentId from Attendance a where a.session.id in :sessionIds")
    List<AttendancePair> findPairsBySessionIds(@Param("sessionIds") List<UUID> sessionIds);

    interface AttendancePair {
        UUID getSessionId();
        UUID getStudentId();
    }
}
