package com.attendance.auth;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_api_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authentication.test-employee-enabled=false",
        "authentication.google-client-id=test-client" })
@AutoConfigureMockMvc
class AuthControllerTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private StudentIdentityRepository students;
    @Autowired private LocalUserRepository users;
    @Autowired private PasswordEncoder passwords;
    @MockitoBean(name = "googleJwtDecoder") private JwtDecoder googleDecoder;

    @BeforeEach
    void resetData() {
        students.deleteAll();
        users.deleteAll();
    }

    @Test
    void employeeLoginReturnsTokenAcceptedByIdentityEndpoint() throws Exception {
        users.save(new LocalUser("profesor", passwords.encode("Profesor123!"),
                "profesor@etf.unibl.org", "EMPLOYEE"));

        String response = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"profesor\",\"password\":\"Profesor123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).get("token").asText();

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("email").value("profesor@etf.unibl.org"))
                .andExpect(jsonPath("role").value("EMPLOYEE"))
                .andExpect(jsonPath("studentId").doesNotExist());
    }

    @Test
    void googleRegistrationCreatesStudentIdentity() throws Exception {
        when(googleDecoder.decode("google-token")).thenReturn(googleToken(
                "marija.stojanovic@student.etf.unibl.org", "student.etf.unibl.org"));
        String googleResponse = mvc.perform(post("/api/auth/google").contentType("application/json")
                        .content("{\"credential\":\"google-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("registrationRequired").value(true))
                .andReturn().getResponse().getContentAsString();
        String registrationToken = json.readTree(googleResponse).get("registration").get("token").asText();
        String body = json.createObjectNode().put("registrationToken", registrationToken)
                .put("indexNumber", "1234/26").put("firstName", "Marija")
                .put("lastName", "Stojanović").toString();

        String response = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).get("token").asText();
        StudentIdentity student = students.findByInstitutionalEmailIgnoreCase(
                "marija.stojanovic@student.etf.unibl.org").orElseThrow();

        assertThat(student.getOidcSubject()).isEqualTo("google-subject");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("role").value("STUDENT"))
                .andExpect(jsonPath("studentId").value(student.getId().toString()));
    }

    @Test
    void rejectsInvalidPasswordAndExternalGoogleDomain() throws Exception {
        users.save(new LocalUser("profesor", passwords.encode("Profesor123!"),
                "profesor@etf.unibl.org", "EMPLOYEE"));
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"profesor\",\"password\":\"pogresna\"}"))
                .andExpect(status().isUnauthorized());

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
                .claim("given_name", "Marija").claim("family_name", "Stojanović").build();
    }
}
