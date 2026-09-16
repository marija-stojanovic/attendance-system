package com.attendance.course.security;

import com.attendance.course.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:security_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityTests {
    @Autowired MockMvc mvc;
    @Autowired StudentRepository students;
    @Autowired IdentityPolicy policy;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach void resetData() { students.deleteAll(); }

    Jwt token(String subject, String email, String role) {
        return Jwt.withTokenValue("test").header("alg", "RS256")
                .issuer("http://localhost:8080/realms/attendance-system")
                .subject(subject).audience(List.of("course-service"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(120))
                .claim("email", email).claim("email_verified", true)
                .claim("realm_access", Map.of("roles", List.of(role))).build();
    }

    @Test void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/students")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test void employeeCanReadStudents() throws Exception {
        when(decoder.decode("test")).thenReturn(token("p", "profesor@etf.org", "EMPLOYEE"));
        mvc.perform(get("/api/students").header("Authorization", "Bearer test")).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization", "Bearer test"))
                .andExpect(status().isOk()).andExpect(jsonPath("role").value("EMPLOYEE"));
    }

    @Test void studentCannotUseEmployeeRoutes() throws Exception {
        when(decoder.decode("test")).thenReturn(token("m", "marija@student.etf.org", "STUDENT"));
        for (String path : List.of("/api/students", "/api/courses", "/api/offerings")) {
            mvc.perform(get(path).header("Authorization", "Bearer test")).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/imports").header("Authorization", "Bearer test"))
                .andExpect(status().isForbidden());
    }

    @Test void linksOnceAndKeepsIdentityWhenEmailChanges() throws Exception {
        Student student = students.saveAndFlush(new Student("TEST-1", "Marija", "Test", "marija@student.etf.org"));
        when(decoder.decode("test")).thenReturn(token("m", "marija@student.etf.org", "STUDENT"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer test"))
                .andExpect(status().isOk()).andExpect(jsonPath("studentId").value(student.getId().toString()));
        when(decoder.decode("test")).thenReturn(token("m", "new@student.etf.org", "STUDENT"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer test"))
                .andExpect(status().isOk()).andExpect(jsonPath("studentId").value(student.getId().toString()));
        when(decoder.decode("test")).thenReturn(token("other", "marija@student.etf.org", "STUDENT"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer test")).andExpect(status().isForbidden());
    }

    @Test void refusesUnknownStudent() throws Exception {
        when(decoder.decode("test")).thenReturn(token("x", "unknown@student.etf.org", "STUDENT"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer test")).andExpect(status().isForbidden());
        assertThat(students.count()).isZero();
    }

    @Test void rejectsWrongDomainAndRole() throws Exception {
        for (Jwt jwt : List.of(token("x", "user@evil.org", "STUDENT"),
                token("x", "user@student.etf.org.evil.org", "STUDENT"),
                token("x", "user@student.etf.org", "EMPLOYEE"),
                token("x", "user@etf.org", "ADMIN"))) {
            when(decoder.decode("test")).thenReturn(jwt);
            mvc.perform(get("/api/me").header("Authorization", "Bearer test")).andExpect(status().isUnauthorized());
        }
    }

    @Test void rejectsUnverifiedEmail() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256").claims(c -> c.putAll(token("m", "marija@student.etf.org", "STUDENT").getClaims())).claim("email_verified", false).build();
        when(decoder.decode("test")).thenReturn(jwt);
        mvc.perform(get("/api/me").header("Authorization", "Bearer test")).andExpect(status().isUnauthorized());
    }

    @Test void rejectsInvalidToken() throws Exception {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("Invalid signature"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized());
    }
}

