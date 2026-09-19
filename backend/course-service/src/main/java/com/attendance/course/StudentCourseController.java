package com.attendance.course;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/student/courses")
public class StudentCourseController {
    private final StudentRepository students;
    private final EnrollmentService enrollments;
    private final GroupManagementService groupManagement;

    public StudentCourseController(StudentRepository students, EnrollmentService enrollments,
            GroupManagementService groupManagement) {
        this.students = students;
        this.enrollments = enrollments;
        this.groupManagement = groupManagement;
    }

    @GetMapping
    public List<StudentCourseResponse> findMyCourses(@AuthenticationPrincipal Jwt jwt) {
        Student student = currentStudent(jwt);
        return enrollments.findCoursesForStudent(student.getId());
    }

    @GetMapping("/{offeringId}/groups")
    public List<StudentGroupSelectionResponse> findGroups(@PathVariable UUID offeringId,
            @AuthenticationPrincipal Jwt jwt) {
        return groupManagement.findStudentChoices(offeringId, currentStudent(jwt).getId());
    }

    @PostMapping("/{offeringId}/groups/{groupId}")
    public StudentGroupSelectionResponse joinGroup(@PathVariable UUID offeringId, @PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt) {
        return groupManagement.join(offeringId, groupId, currentStudent(jwt).getId());
    }

    private Student currentStudent(Jwt jwt) {
        String issuer = jwt.getClaimAsString("identity_issuer");
        String subject = jwt.getClaimAsString("identity_subject");
        return students.findByOidcIssuerAndOidcSubject(issuer, subject)
                .or(() -> students.findFirstByInstitutionalEmailIgnoreCase(jwt.getClaimAsString("email")))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Student nije pronađen u evidenciji."));
    }
}
