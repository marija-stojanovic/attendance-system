package com.attendance.course.security;

import com.attendance.course.Student;
import com.attendance.course.StudentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:security_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StudentRepository students;
    @Autowired LocalUserRepository users;
    @Autowired PasswordEncoder passwords;
    @MockitoBean(name = "googleJwtDecoder") JwtDecoder googleDecoder;

    @BeforeEach
    void resetData() {
        students.deleteAll();
        users.deleteAll();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/students")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCanLogInAndUseEmployeeRoute() throws Exception {
        users.save(new LocalUser("profesor", passwords.encode("Profesor123!"),
                "profesor@etf.unibl.org", "EMPLOYEE"));
        String response = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"profesor\",\"password\":\"Profesor123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).get("token").asText();
        mvc.perform(get("/api/students").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void firstGoogleLoginRegistersStudentAndPreventsEmployeeAccess() throws Exception {
        when(googleDecoder.decode("google-token")).thenReturn(googleToken(
                "marija@student.etf.unibl.org", "student.etf.unibl.org"));
        String response = mvc.perform(post("/api/auth/google").contentType("application/json")
                        .content("{\"credential\":\"google-token\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode challenge = json.readTree(response);
        assertThat(challenge.get("registrationRequired").asBoolean()).isTrue();
        String registrationToken = challenge.get("registration").get("token").asText();
        String registrationBody = json.createObjectNode()
                .put("registrationToken", registrationToken)
                .put("indexNumber", "1234/25")
                .put("firstName", "Marija")
                .put("lastName", "Stojanovic").toString();
        String registrationResponse = mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(registrationBody)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(registrationResponse).get("token").asText();
        Student student = students.findByIndexNumber("1234/25").orElseThrow();
        assertThat(student.getInstitutionalEmail()).isEqualTo("marija@student.etf.unibl.org");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("studentId").value(student.getId().toString()));
        mvc.perform(get("/api/students").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsGoogleAccountOutsideStudentDomain() throws Exception {
        when(googleDecoder.decode("google-token")).thenReturn(googleToken("user@gmail.com", "gmail.com"));
        mvc.perform(post("/api/auth/google").contentType("application/json")
                        .content("{\"credential\":\"google-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    private Jwt googleToken(String email, String domain) {
        return Jwt.withTokenValue("google-token").header("alg", "RS256")
                .issuer("https://accounts.google.com").subject("google-subject")
                .audience(List.of("test-client")).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(120)).claim("email", email)
                .claim("email_verified", true).claim("hd", domain)
                .claim("given_name", "Marija").claim("family_name", "Stojanovic").build();
    }
}
