package com.attendance.course;

import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeachingGroupRepository extends JpaRepository<TeachingGroup, UUID> {
    java.util.Optional<TeachingGroup> findByOfferingIdAndActivityTypeAndGroupNumber(
            UUID offeringId, ActivityType activityType, String groupNumber);
    List<TeachingGroup> findByOfferingIdOrderByActivityTypeAscGroupNumberAsc(UUID offeringId);
    List<TeachingGroup> findByOfferingIdAndActivityTypeOrderByGroupNumberAsc(UUID offeringId, ActivityType activityType);
    List<TeachingGroup> findByOfferingCourseId(UUID courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from TeachingGroup g join fetch g.offering where g.id = :id")
    java.util.Optional<TeachingGroup> findForUpdate(@Param("id") UUID id);
}
