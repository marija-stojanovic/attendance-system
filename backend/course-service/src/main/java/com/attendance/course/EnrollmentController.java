package com.attendance.course;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class EnrollmentController {
    private final EnrollmentService enrollments;

    public EnrollmentController(EnrollmentService enrollments) {
        this.enrollments = enrollments;
    }

    @PostMapping("/offerings/{offeringId}/enrollments")
    public ResponseEntity<EnrollmentResponse> create(@PathVariable UUID offeringId, @Valid @RequestBody CreateEnrollmentRequest request) {
        EnrollmentResponse enrollment = enrollments.create(offeringId, request);
        return ResponseEntity.created(URI.create("/api/enrollments/" + enrollment.id())).body(enrollment);
    }

    @GetMapping("/enrollments/{id}")
    public EnrollmentResponse findById(@PathVariable UUID id) { return enrollments.findById(id); }

    @GetMapping("/offerings/{offeringId}/students")
    public List<EnrollmentResponse> findByOffering(@PathVariable UUID offeringId) {
        return enrollments.findByOffering(offeringId);
    }

    @GetMapping("/courses/{courseId}/students")
    public List<EnrollmentResponse> findByCourse(@PathVariable UUID courseId) {
        return enrollments.findByCourse(courseId);
    }

    @GetMapping("/groups/{groupId}/students")
    public List<EnrollmentResponse> findByGroup(@PathVariable UUID groupId) {
        return enrollments.findByGroup(groupId);
    }

    @PatchMapping("/enrollments/{id}/status")
    public EnrollmentResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateEnrollmentStatusRequest request) {
        return enrollments.updateStatus(id, request);
    }

    @PutMapping("/enrollments/{id}/groups")
    public EnrollmentResponse updateGroups(@PathVariable UUID id, @Valid @RequestBody UpdateEnrollmentGroupsRequest request) {
        return enrollments.updateGroups(id, request);
    }
}
