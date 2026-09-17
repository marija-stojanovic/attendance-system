package com.attendance.course.sessions;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeachingSessionRepository extends JpaRepository<TeachingSession, UUID> {
}
