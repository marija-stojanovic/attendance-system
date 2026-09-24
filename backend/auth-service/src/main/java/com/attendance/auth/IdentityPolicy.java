package com.attendance.auth;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class IdentityPolicy {
    private final String studentDomain;

    public IdentityPolicy(@Value("${authentication.student-domain}") String studentDomain) {
        this.studentDomain = studentDomain.toLowerCase(Locale.ROOT);
    }

    public String role(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (!"STUDENT".equals(role) && !"EMPLOYEE".equals(role)) throw invalidIdentity();
        return role;
    }

    public void verifyGoogleStudent(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        String hostedDomain = jwt.getClaimAsString("hd");
        if (!Boolean.TRUE.equals(jwt.getClaims().get("email_verified"))
                || email == null || email.indexOf('@') <= 0
                || email.indexOf('@') != email.lastIndexOf('@')
                || !email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT).equals(studentDomain)
                || hostedDomain == null || !hostedDomain.toLowerCase(Locale.ROOT).equals(studentDomain)) {
            throw invalidIdentity();
        }
    }

    private OAuth2AuthenticationException invalidIdentity() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"),
                "Nalog nema potvrđenu institucionalnu adresu.");
    }
}
