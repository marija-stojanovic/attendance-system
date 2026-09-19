package com.attendance.course;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/student/courses")
public class StudentCourseController {
    private final StudentRepository students;
    private final EnrollmentService enrollments;

    public StudentCourseController(StudentRepository students, EnrollmentService enrollments) {
        this.students = students;
        this.enrollments = enrollments;
    }

    @GetMapping
    public List<StudentCourseResponse> findMyCourses(@AuthenticationPrincipal Jwt jwt) {
        String issuer = jwt.getClaimAsString("identity_issuer");
        String subject = jwt.getClaimAsString("identity_subject");
        Student student = students.findByOidcIssuerAndOidcSubject(issuer, subject)
                .or(() -> students.findByInstitutionalEmailIgnoreCase(jwt.getClaimAsString("email")))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Student nije pronađen u evidenciji."));
        return enrollments.findCoursesForStudent(student.getId());
    }
}
