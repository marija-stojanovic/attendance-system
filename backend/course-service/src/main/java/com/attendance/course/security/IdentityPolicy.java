package com.attendance.course.security;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class IdentityPolicy {
    private final String studentDomain;
    private final String employeeDomain;

    public IdentityPolicy(@Value("${authentication.student-domain}") String studentDomain,
                          @Value("${authentication.employee-domain}") String employeeDomain) {
        this.studentDomain = studentDomain.toLowerCase(Locale.ROOT);
        this.employeeDomain = employeeDomain.toLowerCase(Locale.ROOT);
    }

    public String role(Jwt jwt) {
        Object access = jwt.getClaims().get("realm_access");
        Object roles = access instanceof Map<?, ?> map ? map.get("roles") : null;
        boolean student = roles instanceof Collection<?> list && list.contains("STUDENT");
        boolean employee = roles instanceof Collection<?> list && list.contains("EMPLOYEE");
        String email = jwt.getClaimAsString("email");
        if (student == employee || !Boolean.TRUE.equals(jwt.getClaims().get("email_verified"))
                || email == null || email.indexOf('@') <= 0
                || email.indexOf('@') != email.lastIndexOf('@')
                || !email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT)
                    .equals(student ? studentDomain : employeeDomain)) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"),
                    "Nalog nema dozvoljenu ulogu ili potvrđenu institucionalnu adresu.");
        }
        return student ? "STUDENT" : "EMPLOYEE";
    }
}
