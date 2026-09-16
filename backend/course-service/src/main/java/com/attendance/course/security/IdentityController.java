package com.attendance.course.security;

import com.attendance.course.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class IdentityController {
    private final StudentRepository students;
    private final IdentityPolicy policy;

    public IdentityController(StudentRepository students, IdentityPolicy policy) {
        this.students = students;
        this.policy = policy;
    }

    @GetMapping("/api/me")
    @Transactional
    public Identity me(@AuthenticationPrincipal Jwt jwt) {
        String role = policy.role(jwt);
        UUID studentId = null;
        if (role.equals("STUDENT")) {
            String issuer = jwt.getIssuer().toString();
            Student student = students.findByOidcIssuerAndOidcSubject(issuer, jwt.getSubject())
                    .orElseGet(() -> {
                        Student match = students.findByInstitutionalEmailIgnoreCase(jwt.getClaimAsString("email"))
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                                        "Student nije pronađen u evidenciji. Obratite se zaposlenom."));
                        if (match.getOidcSubject() != null) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                    "Student je već povezan sa drugim nalogom.");
                        }
                        match.linkIdentity(issuer, jwt.getSubject());
                        return students.saveAndFlush(match);
                    });
            studentId = student.getId();
        }
        return new Identity(jwt.getSubject(), jwt.getClaimAsString("email"), role, studentId);
    }

    public record Identity(String subject, String email, String role, UUID studentId) {}
}
