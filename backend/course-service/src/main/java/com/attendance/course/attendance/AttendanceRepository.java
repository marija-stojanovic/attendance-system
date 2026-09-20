package com.attendance.course.attendance;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    boolean existsBySessionIdAndStudentId(UUID sessionId, UUID studentId);
}
