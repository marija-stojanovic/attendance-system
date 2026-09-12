package com.attendance.course;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class EnrollmentService {
    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final CourseOfferingRepository offerings;
    private final TeachingGroupRepository groups;
    private final CourseRepository courses;

    public EnrollmentService(EnrollmentRepository enrollments, StudentRepository students,
                             CourseOfferingRepository offerings, TeachingGroupRepository groups, CourseRepository courses) {
        this.enrollments = enrollments;
        this.students = students;
        this.offerings = offerings;
        this.groups = groups;
        this.courses = courses;
    }

    @Transactional
    public EnrollmentResponse create(UUID offeringId, CreateEnrollmentRequest request) {
        CourseOffering offering = offerings.findById(offeringId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Izvođenje predmeta nije pronađeno."));
        Student student = students.findById(request.studentId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Student nije pronađen."));
        if (enrollments.existsByStudentIdAndOfferingId(student.getId(), offeringId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student je već upisan na ovo izvođenje predmeta.");
        }
        Set<TeachingGroup> selectedGroups = requireGroups(offeringId, request.groupIds());
        return EnrollmentResponse.from(enrollments.saveAndFlush(new Enrollment(student, offering, request.status(), selectedGroups)));
    }

    public EnrollmentResponse findById(UUID id) {
        return EnrollmentResponse.from(enrollments.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Upis nije pronađen.")));
    }

    public List<EnrollmentResponse> findByOffering(UUID offeringId) {
        if (!offerings.existsById(offeringId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Izvođenje predmeta nije pronađeno.");
        }
        return enrollments.findByOfferingIdOrderByStudentLastNameAscStudentFirstNameAscStudentIndexNumberAsc(offeringId)
                .stream().map(EnrollmentResponse::from).toList();
    }

    public List<EnrollmentResponse> findByCourse(UUID courseId) {
        if (!courses.existsById(courseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Predmet nije pronađen.");
        }
        return enrollments.findByOfferingCourseIdOrderByStudentLastNameAscStudentFirstNameAscStudentIndexNumberAsc(courseId)
                .stream().map(EnrollmentResponse::from).toList();
    }

    public List<EnrollmentResponse> findByGroup(UUID groupId) {
        if (!groups.existsById(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupa nije pronađena.");
        }
        return enrollments.findByGroupsIdOrderByStudentLastNameAscStudentFirstNameAscStudentIndexNumberAsc(groupId)
                .stream().map(EnrollmentResponse::from).toList();
    }

    @Transactional
    public EnrollmentResponse updateStatus(UUID id, UpdateEnrollmentStatusRequest request) {
        Enrollment enrollment = requireForUpdate(id);
        enrollment.setStatus(request.status());
        return EnrollmentResponse.from(enrollment);
    }

    @Transactional
    public EnrollmentResponse updateGroups(UUID id, UpdateEnrollmentGroupsRequest request) {
        Enrollment enrollment = requireForUpdate(id);
        enrollment.replaceGroups(requireGroups(enrollment.getOffering().getId(), request.groupIds()));
        return EnrollmentResponse.from(enrollment);
    }

    private Enrollment requireForUpdate(UUID id) {
        return enrollments.findForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Upis nije pronađen."));
    }

    private Set<TeachingGroup> requireGroups(UUID offeringId, Set<UUID> ids) {
        List<TeachingGroup> selected = groups.findAllById(ids);
        if (selected.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Jedna ili više grupa nisu pronađene.");
        }
        if (selected.stream().anyMatch(group -> !group.getOffering().getId().equals(offeringId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sve grupe moraju pripadati izvođenju predmeta na koje se student upisuje.");
        }
        return new LinkedHashSet<>(selected);
    }
}
