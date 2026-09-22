package com.attendance.course.sessions;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeachingSessionRepository extends JpaRepository<TeachingSession, UUID> {
    List<TeachingSession> findByGroupOfferingCourseIdOrderByDateAscStartTimeAscIdAsc(UUID courseId);
    @EntityGraph(attributePaths = "group")
    List<TeachingSession> findByGroupOfferingIdAndStatus(UUID offeringId, SessionStatus status);
    List<TeachingSession> findByGroupIdIn(List<UUID> groupIds);
    List<TeachingSession> findByStatusAndQrStartedAtLessThanEqual(SessionStatus status, Instant cutoff);
    List<TeachingSession> findByStatus(SessionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TeachingSession s where s.id = :id")
    Optional<TeachingSession> findForUpdate(@Param("id") UUID id);
}
