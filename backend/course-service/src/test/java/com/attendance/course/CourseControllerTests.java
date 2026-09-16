package com.attendance.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.security.test.context.support.WithMockUser(roles = "EMPLOYEE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseRepository courseRepository;

    @BeforeEach
    void setUp() {
        courseRepository.deleteAll();
    }

    @Test
    void createsAndReadsCourse() throws Exception {
        String response = mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", " CS101 ", "name", " Programiranje "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("code").value("CS101"))
                .andExpect(jsonPath("name").value("Programiranje"))
                .andExpect(jsonPath("createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(response).get("id").asText();
        assertThat(courseRepository.findById(UUID.fromString(id))).isPresent();
        mockMvc.perform(get("/api/courses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").value(id))
                .andExpect(jsonPath("code").value("CS101"))
                .andExpect(jsonPath("name").value("Programiranje"));
    }

    @Test
    void returnsLocationOfCreatedCourse() throws Exception {
        var result = mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CS101\",\"name\":\"Programiranje\"}"))
                .andExpect(status().isCreated()).andReturn();
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/courses/" + id);
    }

    @Test
    void returnsCoursesSortedByCode() throws Exception {
        courseRepository.saveAndFlush(new Course("CS202", "Baze podataka"));
        courseRepository.saveAndFlush(new Course("CS101", "Programiranje"));
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("CS101"))
                .andExpect(jsonPath("$[1].code").value("CS202"));
    }

    @Test
    void returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void rejectsDuplicateCode() throws Exception {
        courseRepository.saveAndFlush(new Course("CS101", "Programiranje"));
        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" CS101 \",\"name\":\"Drugi predmet\"}"))
                .andExpect(status().isConflict());
        assertThat(courseRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("invalidCourses")
    void rejectsInvalidCourse(String body, String field) throws Exception {
        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("errors." + field).isNotEmpty());
        assertThat(courseRepository.count()).isZero();
    }

    static Stream<Arguments> invalidCourses() {
        return Stream.of(
                Arguments.of("{\"name\":\"Programiranje\"}", "code"),
                Arguments.of("{\"code\":\"CS101\"}", "name"),
                Arguments.of("{\"code\":\"   \",\"name\":\"Programiranje\"}", "code"),
                Arguments.of("{\"code\":\"CS101\",\"name\":\"   \"}", "name"),
                Arguments.of("{\"code\":\"" + "A".repeat(51) + "\",\"name\":\"Programiranje\"}", "code"),
                Arguments.of("{\"code\":\"CS101\",\"name\":\"" + "A".repeat(256) + "\"}", "name"));
    }

    @Test
    void acceptsMaximumLengths() throws Exception {
        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "A".repeat(50), "name", "N".repeat(255)))))
                .andExpect(status().isCreated());
    }

    @Test
    void returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/courses/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("detail").value("Predmet nije pronađen."));
    }

    @Test
    void rejectsInvalidId() throws Exception {
        mockMvc.perform(get("/api/courses/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        assertThat(courseRepository.count()).isZero();
    }
}
