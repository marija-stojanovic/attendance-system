package com.attendance.course.sessions;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeachingSessionRepository extends JpaRepository<TeachingSession, UUID> {
    List<TeachingSession> findByGroupOfferingCourseIdOrderByDateAscStartTimeAscIdAsc(UUID courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TeachingSession s where s.id = :id")
    Optional<TeachingSession> findForUpdate(@Param("id") UUID id);
}
