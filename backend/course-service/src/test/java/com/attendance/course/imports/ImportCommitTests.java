package com.attendance.course.imports;

import com.attendance.course.*;
import com.attendance.course.security.LocalUser;
import com.attendance.course.security.LocalUserRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

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
    @Autowired private CourseEmployeeRepository courseEmployees;
    @Autowired private LocalUserRepository localUsers;
    @Autowired private ImportPreviewService previews;
    @Autowired private ImportCommitService imports;
    @Autowired private JdbcTemplate jdbc;
    private LocalUser employee;

    @BeforeEach
    void clear() {
        enrollments.deleteAll();
        groups.deleteAll();
        offerings.deleteAll();
        courseEmployees.deleteAll();
        students.deleteAll();
        courses.deleteAll();
        localUsers.deleteAll();
        employee = localUsers.saveAndFlush(new LocalUser("profesor", "hash", "profesor@etf.unibl.org", "EMPLOYEE"));
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
        assertThat(courseEmployees.existsByCourseIdAndEmployeeId(
                java.util.UUID.fromString(first.get("courseId").asText()), employee.getId())).isTrue();
        mvc.perform(get("/api/courses").with(employeeJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/groups/" + first.get("groupId").asText() + "/students").with(employeeJwt()))
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
    void reusesStudentWhenNameUsesDifferentScript() throws Exception {
        students.saveAndFlush(new Student("TEST-002/2099", "Ana Marija", "Primjerić",
                "ana-marija.primjeric@student.etf.unibl.org"));

        JsonNode result = upload(ImportParserTests.CSV, "2026/2027");

        assertThat(result.get("studentsCreated").asInt()).isEqualTo(1);
        assertThat(result.get("studentsReused").asInt()).isEqualTo(1);
        assertThat(students.count()).isEqualTo(2);
    }

    @Test
    void addsGroupWithoutRemovingExistingMembership() throws Exception {
        JsonNode first = upload(ImportParserTests.CSV, "2026/2027");
        JsonNode second = upload(ImportParserTests.CSV.replace(";вјежбе;1", ";лабораторијске вјежбе;2"), "2026/2027");
        assertThat(second.get("enrollmentsCreated").asInt()).isZero();
        assertThat(second.get("groupMembershipsAdded").asInt()).isEqualTo(2);
        assertThat(enrollments.count()).isEqualTo(2);
        for (JsonNode result : List.of(first, second)) {
            mvc.perform(get("/api/groups/" + result.get("groupId").asText() + "/students").with(employeeJwt()))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].groupIds.length()").value(2));
        }
    }

    @Test
    void rejectsInvalidFileWithoutWrites() throws Exception {
        mvc.perform(multipart("/api/imports").file(file(ImportParserTests.CSV.replace("Измишљић;Јана", "Измишљић;")))
                        .param("academicYear", "2026/2027").with(employeeJwt()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("valid").value(false));
        assertThat(courses.count()).isZero();
        assertThat(students.count()).isZero();
        assertThat(enrollments.count()).isZero();
    }

    @Test
    void rollsBackNewCourseGroupAndEarlierStudentWhenLaterStudentConflicts() throws Exception {
        students.saveAndFlush(new Student("TEST-002/2099", "Drugo", "Prezime", "drugo.prezime@student.etf.org"));
        mvc.perform(multipart("/api/imports").file(file(ImportParserTests.CSV)).param("academicYear", "2026/2027")
                        .with(employeeJwt()))
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
        mvc.perform(multipart("/api/imports").file(file(changed)).param("academicYear", "2026/2027")
                        .with(employeeJwt()))
                .andExpect(status().isConflict());
        assertThat(groups.count()).isEqualTo(1);
        mvc.perform(get("/api/groups/" + first.get("groupId").asText() + "/students").with(employeeJwt()))
                .andExpect(jsonPath("$[0].groupIds.length()").value(1));
    }

    @Test
    void rejectsConflictingCourseAndOfferingMetadata() throws Exception {
        upload(ImportParserTests.CSV, "2026/2027");
        for (String changed : List.of(ImportParserTests.CSV.replace("Тестни предмет", "Други назив"),
                ImportParserTests.CSV.replace(";4;8;", ";4;9;"))) {
            mvc.perform(multipart("/api/imports").file(file(changed)).param("academicYear", "2026/2027")
                            .with(employeeJwt()))
                    .andExpect(status().isConflict());
        }
        assertThat(offerings.count()).isEqualTo(1);
        assertThat(enrollments.count()).isEqualTo(2);
    }

    @Test
    void generatesUniqueEmailsForEqualNames() throws Exception {
        upload(ImportParserTests.CSV.replace("Примјерић;Ана Марија", "Измишљић;Јана"), "2026/2027");
        assertThat(students.findAll()).extracting(Student::getInstitutionalEmail)
                .containsExactlyInAnyOrder("jana.izmisljic@student.etf.unibl.org", "jana.izmisljic.2@student.etf.unibl.org");
    }

    @Test
    void requiresValidAcademicYear() throws Exception {
        for (String year : List.of("", "2026", "2026/2029")) {
            mvc.perform(multipart("/api/imports").file(file(ImportParserTests.CSV)).param("academicYear", year)
                            .with(employeeJwt()))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(multipart("/api/imports").file(file(ImportParserTests.CSV)).with(employeeJwt()))
                .andExpect(status().isBadRequest());
        assertThat(courses.count()).isZero();
    }

    @Test
    void repeatsExcelWithoutDuplicates() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            try (var input = getClass().getResourceAsStream("/imports/anonymous-course.xlsx")) {
                mvc.perform(multipart("/api/imports").file(new MockMultipartFile("file", "example.xlsx",
                                        "application/octet-stream", input)).param("academicYear", "2026/2027")
                                .with(employeeJwt()))
                        .andExpect(status().isOk()).andExpect(jsonPath("studentsCreated").value(attempt == 0 ? 2 : 0))
                        .andExpect(jsonPath("enrollmentsCreated").value(attempt == 0 ? 2 : 0));
            }
        }
    }

    @Test
    void assignsFirstYearLaboratoryGroupsAndReportsUncoveredIndex() throws Exception {
        String input = "Predmet;Šifra;Studijski program;Tip studija;Godina studija;Semestar;Oblik nastave;Grupa\n"
                + "Testni predmet;GRUPE-1;Računarstvo i informatika;OSNOVNE;1;1;laboratorijske vježbe;1\n\n"
                + "Prezime;Ime;Indeks;Status\n"
                + "Prvić;Ana;1101/25;REDOVAN\n"
                + "Drugić;Bojan;1122/25;REDOVAN\n"
                + "Trećić;Ceca;1250/25;REDOVAN\n";

        JsonNode result = upload(input, "2025/2026");

        assertThat(result.get("groupsCreated").asInt()).isEqualTo(4);
        assertThat(result.get("groupIds")).hasSize(4);
        assertThat(result.get("groupMembershipsAdded").asInt()).isEqualTo(5);
        assertThat(result.get("warnings")).hasSize(1);
        assertThat(result.get("warnings").get(0).get("message").asText()).contains("1250/25");
        assertThat(groups.findAll()).extracting(TeachingGroup::getGroupNumber)
                .containsExactlyInAnyOrder("G1", "G3", "L1", "L2");

        Integer uncoveredMemberships = jdbc.queryForObject("""
                select count(*) from enrollment_groups eg
                join enrollments e on e.id = eg.enrollment_id
                join students s on s.id = e.student_id
                where s.index_number = '1250/25'
                """, Integer.class);
        assertThat(uncoveredMemberships).isEqualTo(1);

        Student student = students.findByIndexNumber("1101/25").orElseThrow();
        student.linkIdentity("https://accounts.google.com", "student-subject");
        students.saveAndFlush(student);
        mvc.perform(get("/api/student/courses").with(studentJwt("student-subject", student.getInstitutionalEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseCode").value("GRUPE-1"))
                .andExpect(jsonPath("$[0].groups.length()").value(2))
                .andExpect(jsonPath("$[0].groups[0].activityType").value("AUDITORY_EXERCISE"))
                .andExpect(jsonPath("$[0].groups[0].groupNumber").value("G1"))
                .andExpect(jsonPath("$[0].groups[1].activityType").value("LABORATORY_EXERCISE"))
                .andExpect(jsonPath("$[0].groups[1].groupNumber").value("L1"));
    }

    @Test
    void configuresOlderYearGroupsAndAllowsStudentSelection() throws Exception {
        StringBuilder input = new StringBuilder()
                .append("Predmet;Šifra;Studijski program;Tip studija;Godina studija;Semestar;Oblik nastave;Grupa\n")
                .append("Stariji predmet;STARIJI-1;Računarstvo;OSNOVNE;3;5;vježbe;1\n\n")
                .append("Prezime;Ime;Indeks;Status\n");
        for (int number = 1; number <= 45; number++) {
            input.append("Prezime;Ime;TEST-")
                    .append(String.format("%03d", number)).append("/2099;REDOVAN\n");
        }
        JsonNode imported = upload(input.toString(), "2026/2027");
        String offeringId = imported.get("offeringId").asText();

        mvc.perform(get("/api/offerings/" + offeringId + "/group-settings").with(employeeJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].studentCount").value(45))
                .andExpect(jsonPath("$[1].configured").value(false));

        String response = mvc.perform(put("/api/offerings/" + offeringId
                        + "/group-settings/LABORATORY_EXERCISE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupCount\":3,\"groupCapacity\":20,\"selectionEnabled\":true}")
                        .with(employeeJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("groups.length()").value(3))
                .andExpect(jsonPath("groups[0].capacity").value(20))
                .andExpect(jsonPath("groups[1].capacity").value(20))
                .andExpect(jsonPath("groups[2].capacity").value(20))
                .andReturn().getResponse().getContentAsString();
        String firstGroupId = json.readTree(response).get("groups").get(0).get("id").asText();
        String secondGroupId = json.readTree(response).get("groups").get(1).get("id").asText();

        Student student = students.findByIndexNumber("TEST-001/2099").orElseThrow();
        student.linkIdentity("https://accounts.google.com", "older-student");
        students.saveAndFlush(student);
        var studentToken = studentJwt("older-student", student.getInstitutionalEmail());
        mvc.perform(post("/api/student/courses/" + offeringId + "/groups/" + firstGroupId).with(studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("currentGroupId").value(firstGroupId))
                .andExpect(jsonPath("groups[0].occupied").value(1));
        mvc.perform(post("/api/student/courses/" + offeringId + "/groups/" + secondGroupId).with(studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("currentGroupId").value(secondGroupId))
                .andExpect(jsonPath("groups[0].occupied").value(0))
                .andExpect(jsonPath("groups[1].occupied").value(1));
    }

    @Test
    void deletesCourseAndKeepsStudentsInDatabase() throws Exception {
        JsonNode imported = upload(ImportParserTests.CSV, "2026/2027");
        Student student = students.findByIndexNumber("TEST-001/2099").orElseThrow();
        student.linkIdentity("https://accounts.google.com", "deleted-student");
        students.saveAndFlush(student);

        mvc.perform(delete("/api/courses/" + imported.get("courseId").asText()).with(employeeJwt()))
                .andExpect(status().isNoContent());

        assertThat(courses.count()).isZero();
        assertThat(offerings.count()).isZero();
        assertThat(groups.count()).isZero();
        assertThat(enrollments.count()).isZero();
        assertThat(students.count()).isEqualTo(2);
        mvc.perform(get("/api/student/courses").with(studentJwt("deleted-student",
                        "jana.izmisljic@student.etf.unibl.org")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void concurrentUploadsRemainIdempotent() throws Exception {
        var preview = previews.preview(file(ImportParserTests.CSV), "etf", null);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<ImportResult> operation = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return imports.commit(preview, "2026/2027", employee.getId());
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
        var response = mvc.perform(multipart("/api/imports").file(file(text)).param("academicYear", year)
                        .with(employeeJwt()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private RequestPostProcessor employeeJwt() {
        return jwt().jwt(token -> token.subject(employee.getId().toString()).claim("role", "EMPLOYEE"))
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
    }

    private RequestPostProcessor studentJwt(String subject, String email) {
        return jwt().jwt(token -> token.subject(subject)
                        .claim("role", "STUDENT")
                        .claim("email", email)
                        .claim("identity_issuer", "https://accounts.google.com")
                        .claim("identity_subject", subject))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }
}
