package com.attendance.course.imports;

import com.attendance.course.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=${IMPORT_TEST_DB_URL:jdbc:h2:mem:import_commit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportCommitTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private CourseRepository courses;
    @Autowired private CourseOfferingRepository offerings;
    @Autowired private TeachingGroupRepository groups;
    @Autowired private StudentRepository students;
    @Autowired private EnrollmentRepository enrollments;
    @Autowired private ImportPreviewService previews;
    @Autowired private ImportCommitService imports;

    @BeforeEach
    void clear() {
        enrollments.deleteAll();
        groups.deleteAll();
        offerings.deleteAll();
        students.deleteAll();
        courses.deleteAll();
    }

    @Test
    void importsAndRepeatsWithoutDuplicates() throws Exception {
        JsonNode first = upload(ImportParserTests.CSV, "2026/2027");
        assertThat(first.get("studentsCreated").asInt()).isEqualTo(2);
        assertThat(first.get("enrollmentsCreated").asInt()).isEqualTo(2);
        assertThat(first.get("groupMembershipsAdded").asInt()).isEqualTo(2);
        JsonNode second = upload(ImportParserTests.CSV, "2026/2027");
        assertThat(second.get("courseId")).isEqualTo(first.get("courseId"));
        assertThat(second.get("offeringId")).isEqualTo(first.get("offeringId"));
        assertThat(second.get("groupId")).isEqualTo(first.get("groupId"));
        assertThat(second.get("studentsCreated").asInt()).isZero();
        assertThat(second.get("enrollmentsCreated").asInt()).isZero();
        assertThat(second.get("studentsReused").asInt()).isEqualTo(2);
        assertThat(second.get("groupMembershipsAdded").asInt()).isZero();
        assertThat(courses.count()).isEqualTo(1);
        assertThat(students.count()).isEqualTo(2);
        assertThat(enrollments.count()).isEqualTo(2);
        mvc.perform(get("/api/groups/" + first.get("groupId").asText() + "/students"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void reusesStudentsAcrossYearsAndCourses() throws Exception {
        upload(ImportParserTests.CSV, "2026/2027");
        JsonNode nextYear = upload(ImportParserTests.CSV, "2027/2028");
        assertThat(nextYear.get("studentsCreated").asInt()).isZero();
        assertThat(nextYear.get("enrollmentsCreated").asInt()).isEqualTo(2);
        upload(ImportParserTests.CSV.replace("DEMO-101", "DEMO-102"), "2026/2027");
        assertThat(students.count()).isEqualTo(2);
        assertThat(courses.count()).isEqualTo(2);
        assertThat(offerings.count()).isEqualTo(3);
        assertThat(enrollments.count()).isEqualTo(6);
    }

    @Test
    void addsGroupWithoutRemovingExistingMembership() throws Exception {
        JsonNode first = upload(ImportParserTests.CSV, "2026/2027");
        JsonNode second = upload(ImportParserTests.CSV.replace(";вјежбе;1", ";лабораторијске вјежбе;2"), "2026/2027");
        assertThat(second.get("enrollmentsCreated").asInt()).isZero();
        assertThat(second.get("groupMembershipsAdded").asInt()).isEqualTo(2);
        assertThat(enrollments.count()).isEqualTo(2);
        for (JsonNode result : List.of(first, second)) {
            mvc.perform(get("/api/groups/" + result.get("groupId").asText() + "/students"))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].groupIds.length()").value(2));
        }
    }

    @Test
    void rejectsInvalidFileWithoutWrites() throws Exception {
        mvc.perform(multipart("/api/imports").file(file(ImportParserTests.CSV.replace("Измишљић;Јана", "Измишљић;")))
                        .param("academicYear", "2026/2027"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("valid").value(false));
        assertThat(courses.count()).isZero();
        assertThat(students.count()).isZero();
        assertThat(enrollments.count()).isZero();
    }

    @Test
    void rollsBackNewCourseGroupAndEarlierStudentWhenLaterStudentConflicts() throws Exception {
        students.saveAndFlush(new Student("TEST-002/2099", "Drugo", "Prezime", "drugo.prezime@student.etf.org"));
        mvc.perform(multipart("/api/imports").file(file(ImportParserTests.CSV)).param("academicYear", "2026/2027"))
                .andExpect(status().isConflict()).andExpect(jsonPath("detail").value(org.hamcrest.Matchers.containsString("Red 7")));
        assertThat(courses.count()).isZero();
        assertThat(offerings.count()).isZero();
        assertThat(groups.count()).isZero();
        assertThat(enrollments.count()).isZero();
        assertThat(students.count()).isEqualTo(1);
        assertThat(students.existsByIndexNumber("TEST-001/2099")).isFalse();
    }

    @Test
    void rollsBackGroupMembershipsOnStatusConflict() throws Exception {
        JsonNode first = upload(ImportParserTests.CSV, "2026/2027");
        String changed = ImportParserTests.CSV.replace(";вјежбе;1", ";вјежбе;2").replace(";ОБНОВА", ";НОВИ");
        mvc.perform(multipart("/api/imports").file(file(changed)).param("academicYear", "2026/2027"))
                .andExpect(status().isConflict());
        assertThat(groups.count()).isEqualTo(1);
        mvc.perform(get("/api/groups/" + first.get("groupId").asText() + "/students"))
                .andExpect(jsonPath("$[0].groupIds.length()").value(1));
    }

    @Test
    void rejectsConflictingCourseAndOfferingMetadata() throws Exception {
        upload(ImportParserTests.CSV, "2026/2027");
        for (String changed : List.of(ImportParserTests.CSV.replace("Тестни предмет", "Други назив"),
                ImportParserTests.CSV.replace(";1;2;", ";1;3;"))) {
            mvc.perform(multipart("/api/imports").file(file(changed)).param("academicYear", "2026/2027"))
                    .andExpect(status().isConflict());
        }
        assertThat(offerings.count()).isEqualTo(1);
        assertThat(enrollments.count()).isEqualTo(2);
    }

    @Test
    void generatesUniqueEmailsForEqualNames() throws Exception {
        upload(ImportParserTests.CSV.replace("Примјерић;Ана Марија", "Измишљић;Јана"), "2026/2027");
        assertThat(students.findAll()).extracting(Student::getInstitutionalEmail)
                .containsExactlyInAnyOrder("jana.izmisljic@student.etf.org", "jana.izmisljic.2@student.etf.org");
    }

    @Test
    void requiresValidAcademicYear() throws Exception {
        for (String year : List.of("", "2026", "2026/2029")) {
            mvc.perform(multipart("/api/imports").file(file(ImportParserTests.CSV)).param("academicYear", year))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(multipart("/api/imports").file(file(ImportParserTests.CSV))).andExpect(status().isBadRequest());
        assertThat(courses.count()).isZero();
    }

    @Test
    void acceptsExcelAndReusesCsvData() throws Exception {
        upload(ImportParserTests.CSV, "2026/2027");
        try (var input = getClass().getResourceAsStream("/imports/anonymous-course.xlsx")) {
            mvc.perform(multipart("/api/imports").file(new MockMultipartFile("file", "example.xlsx", "application/octet-stream", input))
                            .param("academicYear", "2026/2027"))
                    .andExpect(status().isOk()).andExpect(jsonPath("studentsCreated").value(0))
                    .andExpect(jsonPath("enrollmentsCreated").value(0));
        }
    }

    @Test
    void concurrentUploadsRemainIdempotent() throws Exception {
        var preview = previews.preview(file(ImportParserTests.CSV), "etf", null);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<ImportResult> operation = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return imports.commit(preview, "2026/2027");
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = executor.invokeAll(List.of(operation, operation), 40, TimeUnit.SECONDS);
            var first = futures.get(0).get();
            var second = futures.get(1).get();
            assertThat(first.offeringId()).isEqualTo(second.offeringId());
            assertThat(first.studentsCreated() + second.studentsCreated()).isEqualTo(2);
        }
        assertThat(courses.count()).isEqualTo(1);
        assertThat(students.count()).isEqualTo(2);
        assertThat(enrollments.count()).isEqualTo(2);
    }

    private MockMultipartFile file(String text) {
        return new MockMultipartFile("file", "example.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode upload(String text, String year) throws Exception {
        var response = mvc.perform(multipart("/api/imports").file(file(text)).param("academicYear", year))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }
}
