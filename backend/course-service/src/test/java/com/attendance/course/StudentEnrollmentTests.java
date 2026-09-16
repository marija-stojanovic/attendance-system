package com.attendance.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=${ENROLLMENT_TEST_DB_URL:jdbc:h2:mem:student_enrollment_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}")
@org.springframework.security.test.context.support.WithMockUser(roles = "EMPLOYEE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentEnrollmentTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private StudentRepository students;
    @Autowired private CourseRepository courses;
    @Autowired private CourseOfferingRepository offerings;
    @Autowired private TeachingGroupRepository groups;
    @Autowired private EnrollmentRepository enrollments;
    @Autowired private StudentService studentService;
    @Autowired private EnrollmentService enrollmentService;

    @BeforeEach
    void clearDatabase() {
        enrollments.deleteAll();
        groups.deleteAll();
        offerings.deleteAll();
        students.deleteAll();
        courses.deleteAll();
    }

    @Test
    void createsStudentAndPreservesName() throws Exception {
        var result = mvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("indexNumber", " 001/2026 ",
                                "firstName", " Ђорђе ", "lastName", "Чавић"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("indexNumber").value("001/2026"))
                .andExpect(jsonPath("firstName").value("Ђорђе"))
                .andExpect(jsonPath("institutionalEmail").value("djordje.cavic@student.etf.org"))
                .andExpect(jsonPath("createdAt").isNotEmpty()).andReturn();
        String id = json.readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/students/" + id);
        mvc.perform(get("/api/students/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("lastName").value("Чавић"));
        mvc.perform(get("/api/students")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void generatesDifferentEmailsForSameNamesAndTransliterations() throws Exception {
        student("1/2026", "Đorđe", "Čavić");
        JsonNode second = student("2/2026", "Ђорђе", "Чавић");
        JsonNode third = student("3/2026", "Djordje", "Cavic");
        assertThat(second.get("institutionalEmail").asText()).isEqualTo("djordje.cavic.2@student.etf.org");
        assertThat(third.get("institutionalEmail").asText()).isEqualTo("djordje.cavic.3@student.etf.org");
    }

    @Test
    void handlesConcurrentEmailCollisions() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(4);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Callable<StudentResponse>> calls = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(i -> (Callable<StudentResponse>) () -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        return studentService.create(new CreateStudentRequest(i + "/2026", "Ana", "Anić"));
                    }).toList();
            var results = executor.invokeAll(calls, 30, TimeUnit.SECONDS);
            var emails = new java.util.HashSet<String>();
            for (var result : results) {
                emails.add(result.get().institutionalEmail());
            }
            assertThat(emails).hasSize(4);
            assertThat(students.count()).isEqualTo(4);
        }
    }

    @Test
    void preventsConcurrentDuplicateEnrollments() throws Exception {
        UUID offering = offering(course(), "2026/2027");
        UUID student = UUID.fromString(student("1/2026", "Ana", "Anić").get("id").asText());
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Boolean> call = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                enrollmentService.create(offering, new CreateEnrollmentRequest(student, "REDOVAN", Set.of()));
                return true;
            } catch (org.springframework.dao.DataIntegrityViolationException exception) {
                return false;
            } catch (org.springframework.web.server.ResponseStatusException exception) {
                assertThat(exception.getStatusCode().value()).isEqualTo(409);
                return false;
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(call, call), 30, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(enrollments.count()).isEqualTo(1);
    }

    @Test
    void isolatesStudentsBetweenCoursesAndGroups() throws Exception {
        UUID course = course();
        UUID offering = offering(course, "2026/2027");
        UUID group = group(offering, ActivityType.LECTURE);
        UUID otherOffering = offering(course(), "2026/2027");
        UUID otherGroup = group(otherOffering, ActivityType.LECTURE);
        String ana = student("1/2026", "Ana", "Anić").get("id").asText();
        String marko = student("2/2026", "Marko", "Marić").get("id").asText();
        create("/api/offerings/" + offering + "/enrollments",
                Map.of("studentId", ana, "status", "REDOVAN", "groupIds", List.of(group)));
        create("/api/offerings/" + otherOffering + "/enrollments",
                Map.of("studentId", marko, "status", "REDOVAN", "groupIds", List.of(otherGroup)));
        for (String path : List.of("/api/courses/" + course + "/students", "/api/offerings/" + offering + "/students",
                "/api/groups/" + group + "/students")) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].student.id").value(ana));
        }
    }

    @Test
    void rejectsDuplicateIndex() throws Exception {
        student("1/2026", "Ana", "Anić");
        mvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("indexNumber", " 1/2026 ", "firstName", "Marko", "lastName", "Marić"))))
                .andExpect(status().isConflict());
        assertThat(students.count()).isEqualTo(1);
    }

    @Test
    void rejectsMissingBlankAndTooLongStudentFields() throws Exception {
        for (String field : List.of("indexNumber", "firstName", "lastName")) {
            for (String invalid : List.of("", "   ", "A".repeat(field.equals("indexNumber") ? 31 : 101))) {
                var body = new java.util.HashMap<>(Map.of("indexNumber", "1/2026", "firstName", "Ana", "lastName", "Anić"));
                body.put(field, invalid);
                mvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("errors." + field).exists());
            }
        }
        mvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(students.count()).isZero();
    }

    @Test
    void rejectsNamesThatCannotProduceAnEmail() throws Exception {
        for (String name : List.of("Ana123", "---", "李")) {
            mvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("indexNumber", "1/2026", "firstName", name, "lastName", "Anić"))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(students.count()).isZero();
    }

    @Test
    void createsOfferingAndGroupsThroughApi() throws Exception {
        UUID course = course();
        JsonNode offering = create("/api/courses/" + course + "/offerings", offeringBody("2026/2027"));
        String offeringId = offering.get("id").asText();
        mvc.perform(get("/api/offerings/" + offeringId)).andExpect(status().isOk());
        mvc.perform(get("/api/courses/" + course + "/offerings")).andExpect(jsonPath("$.length()").value(1));
        JsonNode group = create("/api/offerings/" + offeringId + "/groups",
                Map.of("activityType", "LECTURE", "groupNumber", " 1 "));
        mvc.perform(get("/api/groups/" + group.get("id").asText())).andExpect(status().isOk())
                .andExpect(jsonPath("groupNumber").value("1"));
        mvc.perform(get("/api/offerings/" + offeringId + "/groups")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void enrollsStudentInMultipleGroupsAndReadsLists() throws Exception {
        UUID course = course();
        UUID offering = offering(course, "2026/2027");
        UUID lecture = group(offering, ActivityType.LECTURE);
        UUID lab = group(offering, ActivityType.LABORATORY_EXERCISE);
        String studentId = student("1/2026", "Ana", "Anić").get("id").asText();
        JsonNode enrollment = create("/api/offerings/" + offering + "/enrollments",
                Map.of("studentId", studentId, "status", " REDOVAN ", "groupIds", List.of(lecture, lab)));
        assertThat(enrollment.get("status").asText()).isEqualTo("REDOVAN");
        assertThat(enrollment.get("groupIds").size()).isEqualTo(2);
        for (String path : List.of("/api/courses/" + course + "/students", "/api/offerings/" + offering + "/students",
                "/api/groups/" + lecture + "/students", "/api/groups/" + lab + "/students")) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].student.id").value(studentId))
                    .andExpect(jsonPath("$[0].academicYear").value("2026/2027"));
        }
        mvc.perform(get("/api/enrollments/" + enrollment.get("id").asText())).andExpect(status().isOk());
    }

    @Test
    void preventsDuplicateEnrollmentButAllowsNextAcademicYear() throws Exception {
        UUID course = course();
        UUID offering = offering(course, "2026/2027");
        String student = student("1/2026", "Ana", "Anić").get("id").asText();
        var body = Map.of("studentId", student, "status", "REDOVAN");
        create("/api/offerings/" + offering + "/enrollments", body);
        mvc.perform(post("/api/offerings/" + offering + "/enrollments").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body))).andExpect(status().isConflict());
        create("/api/offerings/" + offering(course, "2027/2028") + "/enrollments", body);
        assertThat(enrollments.count()).isEqualTo(2);
    }

    @Test
    void rejectsGroupFromAnotherOfferingWithoutSavingEnrollment() throws Exception {
        UUID course = course();
        UUID offering = offering(course, "2026/2027");
        UUID otherGroup = group(offering(course, "2027/2028"), ActivityType.LECTURE);
        String student = student("1/2026", "Ana", "Anić").get("id").asText();
        mvc.perform(post("/api/offerings/" + offering + "/enrollments").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("studentId", student, "status", "REDOVAN", "groupIds", List.of(otherGroup)))))
                .andExpect(status().isBadRequest());
        assertThat(enrollments.count()).isZero();
    }

    @Test
    void updatesStatusAndReplacesOrClearsGroups() throws Exception {
        UUID offering = offering(course(), "2026/2027");
        UUID first = group(offering, ActivityType.LECTURE);
        UUID second = group(offering, ActivityType.LABORATORY_EXERCISE);
        String student = student("1/2026", "Ana", "Anić").get("id").asText();
        String enrollment = create("/api/offerings/" + offering + "/enrollments",
                Map.of("studentId", student, "status", "REDOVAN", "groupIds", List.of(first))).get("id").asText();
        mvc.perform(patch("/api/enrollments/" + enrollment + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OBNOVA\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/enrollments/" + enrollment + "/groups").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("groupIds", List.of(second)))))
                .andExpect(status().isOk()).andExpect(jsonPath("groupIds.length()").value(1));
        mvc.perform(get("/api/groups/" + first + "/students")).andExpect(content().json("[]"));
        mvc.perform(get("/api/groups/" + second + "/students")).andExpect(jsonPath("$[0].status").value("OBNOVA"));
        mvc.perform(put("/api/enrollments/" + enrollment + "/groups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupIds\":[]}")).andExpect(status().isOk());
        mvc.perform(get("/api/groups/" + second + "/students")).andExpect(content().json("[]"));
    }

    @Test
    void rejectsInvalidGroupReplacementAndPreservesMembership() throws Exception {
        UUID offering = offering(course(), "2026/2027");
        UUID group = group(offering, ActivityType.LECTURE);
        UUID other = group(offering(course(), "2026/2027"), ActivityType.LECTURE);
        String student = student("1/2026", "Ana", "Anić").get("id").asText();
        String enrollment = create("/api/offerings/" + offering + "/enrollments",
                Map.of("studentId", student, "status", "REDOVAN", "groupIds", List.of(group))).get("id").asText();
        mvc.perform(put("/api/enrollments/" + enrollment + "/groups").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("groupIds", List.of(other)))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/groups/" + group + "/students")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rejectsMissingReferencesAndInvalidStatus() throws Exception {
        UUID offering = offering(course(), "2026/2027");
        String student = student("1/2026", "Ana", "Anić").get("id").asText();
        for (var body : List.of(Map.of("studentId", UUID.randomUUID().toString(), "status", "REDOVAN"),
                Map.of("studentId", student, "status", "REDOVAN", "groupIds", List.of(UUID.randomUUID())))) {
            mvc.perform(post("/api/offerings/" + offering + "/enrollments").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(body))).andExpect(status().isNotFound());
        }
        for (String status : List.of(" ", "A".repeat(31))) {
            mvc.perform(post("/api/offerings/" + offering + "/enrollments").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("studentId", student, "status", status))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(enrollments.count()).isZero();
    }

    @Test
    void returnsEmptyListsAndNotFoundForUnknownParents() throws Exception {
        UUID course = course();
        UUID offering = offering(course, "2026/2027");
        UUID group = group(offering, ActivityType.LECTURE);
        for (String path : List.of("/api/courses/" + course + "/students", "/api/offerings/" + offering + "/students",
                "/api/groups/" + group + "/students")) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().json("[]"));
        }
        for (String resource : List.of("students", "enrollments", "offerings", "groups")) {
            mvc.perform(get("/api/" + resource + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
        }
        for (String resource : List.of("courses", "offerings", "groups")) {
            mvc.perform(get("/api/" + resource + "/" + UUID.randomUUID() + "/students")).andExpect(status().isNotFound());
        }
    }

    @Test
    void rejectsDuplicateOfferingsAndGroups() throws Exception {
        UUID course = course();
        UUID offering = offering(course, "2026/2027");
        mvc.perform(post("/api/courses/" + course + "/offerings").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(offeringBody("2026/2027")))).andExpect(status().isConflict());
        group(offering, ActivityType.LECTURE);
        mvc.perform(post("/api/offerings/" + offering + "/groups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityType\":\"LECTURE\",\"groupNumber\":\"1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidOfferingAndGroupValues() throws Exception {
        UUID course = course();
        for (String year : List.of("2026", "2026/2029")) {
            mvc.perform(post("/api/courses/" + course + "/offerings").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(offeringBody(year)))).andExpect(status().isBadRequest());
        }
        UUID offering = offering(course, "2026/2027");
        mvc.perform(post("/api/offerings/" + offering + "/groups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityType\":\"UNKNOWN\",\"groupNumber\":\"1\"}"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode student(String index, String first, String last) throws Exception {
        return create("/api/students", Map.of("indexNumber", index, "firstName", first, "lastName", last));
    }

    private UUID course() {
        return courses.saveAndFlush(new Course(UUID.randomUUID().toString(), "Programiranje")).getId();
    }

    private UUID offering(UUID course, String year) throws Exception {
        return UUID.fromString(create("/api/courses/" + course + "/offerings", offeringBody(year)).get("id").asText());
    }

    private Map<String, Object> offeringBody(String year) {
        return Map.of("academicYear", year, "studyProgram", "Računarstvo", "studyType", "OSNOVNE", "yearOfStudy", 1, "semester", 1);
    }

    private UUID group(UUID offering, ActivityType type) throws Exception {
        return UUID.fromString(create("/api/offerings/" + offering + "/groups",
                Map.of("activityType", type.name(), "groupNumber", "1")).get("id").asText());
    }

    private JsonNode create(String path, Object body) throws Exception {
        String response = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }
}
