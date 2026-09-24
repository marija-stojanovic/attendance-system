package com.attendance.auth;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_token_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authentication.test-employee-enabled=false",
        "authentication.google-client-id=test-client" })
class TokenServiceTests {
    @Autowired private TokenService tokens;
    @Autowired @Qualifier("applicationJwtDecoder") private JwtDecoder decoder;

    @Test
    void issuesApplicationTokenWithExistingClaims() {
        var response = tokens.issue("employee-id", "profesor@etf.unibl.org", "EMPLOYEE",
                "local", "employee-id");
        var jwt = decoder.decode(response.token());

        assertThat(jwt.getSubject()).isEqualTo("employee-id");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("profesor@etf.unibl.org");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("EMPLOYEE");
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://attendance-system.local");
        assertThat(jwt.getAudience()).containsExactly("course-service");
        assertThat(response.expiresAt()).isAfter(Instant.now().plusSeconds(1700));
    }

    @Test
    void issuesStudentRegistrationChallengeWithExistingClaims() {
        Instant now = Instant.now();
        Jwt google = new Jwt("google-token", now, now.plusSeconds(300),
                Map.of("alg", "RS256"), Map.of(
                        "iss", "https://accounts.google.com",
                        "sub", "google-student",
                        "email", "marija.stojanovic@student.etf.unibl.org",
                        "given_name", "Marija",
                        "family_name", "Stojanović"));

        var response = tokens.issueRegistration(google);
        var jwt = decoder.decode(response.token());

        assertThat(jwt.getClaimAsString("purpose")).isEqualTo("student_registration");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("marija.stojanovic@student.etf.unibl.org");
        assertThat(jwt.getClaimAsString("first_name")).isEqualTo("Marija");
        assertThat(jwt.getClaimAsString("last_name")).isEqualTo("Stojanović");
        assertThat(jwt.getClaimAsString("identity_subject")).isEqualTo("google-student");
    }
}
