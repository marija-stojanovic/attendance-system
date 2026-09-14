package com.attendance.course;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeachingGroupRepository extends JpaRepository<TeachingGroup, UUID> {
    java.util.Optional<TeachingGroup> findByOfferingIdAndActivityTypeAndGroupNumber(
            UUID offeringId, ActivityType activityType, String groupNumber);
    List<TeachingGroup> findByOfferingIdOrderByActivityTypeAscGroupNumberAsc(UUID offeringId);
}
