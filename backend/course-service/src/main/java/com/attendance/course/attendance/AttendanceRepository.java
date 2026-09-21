package com.attendance.course.attendance;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    boolean existsBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    List<Attendance> findBySessionIdOrderByRecordedAtAscStudentLastNameAscStudentFirstNameAsc(UUID sessionId);
    Optional<Attendance> findByIdAndSessionId(UUID id, UUID sessionId);
}
