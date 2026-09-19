package com.attendance.course;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OfferingController {
    private final OfferingService offerings;
    private final GroupManagementService groupManagement;

    public OfferingController(OfferingService offerings, GroupManagementService groupManagement) {
        this.offerings = offerings;
        this.groupManagement = groupManagement;
    }

    @PostMapping("/courses/{courseId}/offerings")
    public ResponseEntity<OfferingResponse> create(@PathVariable UUID courseId, @Valid @RequestBody CreateOfferingRequest request) {
        OfferingResponse offering = offerings.create(courseId, request);
        return ResponseEntity.created(URI.create("/api/offerings/" + offering.id())).body(offering);
    }

    @GetMapping("/courses/{courseId}/offerings")
    public List<OfferingResponse> findByCourse(@PathVariable UUID courseId) {
        return offerings.findByCourse(courseId);
    }

    @GetMapping("/offerings/{id}")
    public OfferingResponse findById(@PathVariable UUID id) {
        return offerings.findById(id);
    }

    @PostMapping("/offerings/{offeringId}/groups")
    public ResponseEntity<GroupResponse> createGroup(@PathVariable UUID offeringId, @Valid @RequestBody CreateGroupRequest request) {
        GroupResponse group = offerings.createGroup(offeringId, request);
        return ResponseEntity.created(URI.create("/api/groups/" + group.id())).body(group);
    }

    @GetMapping("/offerings/{offeringId}/groups")
    public List<GroupResponse> findGroups(@PathVariable UUID offeringId) {
        return offerings.findGroups(offeringId);
    }

    @GetMapping("/groups/{id}")
    public GroupResponse findGroup(@PathVariable UUID id) {
        return offerings.findGroup(id);
    }

    @GetMapping("/offerings/{offeringId}/group-settings")
    public List<ActivityGroupSettingsResponse> findGroupSettings(@PathVariable UUID offeringId,
            @AuthenticationPrincipal Jwt jwt) {
        return groupManagement.findSettings(offeringId, UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/offerings/{offeringId}/group-settings/{activityType}")
    public ActivityGroupSettingsResponse configureGroups(@PathVariable UUID offeringId,
            @PathVariable ActivityType activityType, @Valid @RequestBody GroupSettingsRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return groupManagement.configure(offeringId, activityType, request, UUID.fromString(jwt.getSubject()));
    }
}
