package com.attendance.course.attendance;

import com.attendance.course.*;
import com.attendance.course.sessions.*;
import com.attendance.course.security.LocalUser;
import com.attendance.course.security.LocalUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:attendance_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "session-qr.rotation-seconds=10" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private AttendanceRepository attendances;
    @Autowired private TeachingSessionRepository sessions;
    @Autowired private TeachingSessionService sessionService;
    @Autowired private SessionQrService qr;
    @Autowired private EnrollmentRepository enrollments;
    @Autowired private StudentRepository students;
    @Autowired private TeachingGroupRepository groups;
    @Autowired private CourseOfferingRepository offerings;
    @Autowired private CourseRepository courses;
    @Autowired private CourseEmployeeRepository courseEmployees;
    @Autowired private LocalUserRepository localUsers;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private Clock qrClock;

    private final Instant start = Instant.parse("2026-10-01T09:00:00Z");
    private Student student;
    private CourseOffering offering;
    private TeachingGroup lecture;
    private Course course;
    private LocalUser employee;

    @BeforeEach
    void setUp() {
        when(qrClock.instant()).thenReturn(start);
        attendances.deleteAll();
        sessions.deleteAll();
        enrollments.deleteAll();
        students.deleteAll();
        groups.deleteAll();
        offerings.deleteAll();
        courseEmployees.deleteAll();
        courses.deleteAll();
        localUsers.deleteAll();
        course = courses.saveAndFlush(new Course("ATT-1", "Evidencija prisustva"));
        employee = localUsers.saveAndFlush(new LocalUser("attendance-professor", "hash",
                "attendance.professor@etf.unibl.org", "EMPLOYEE"));
        courseEmployees.saveAndFlush(new CourseEmployee(course, employee, CourseEmployeeRole.TEACHER));
        offering = offerings.saveAndFlush(new CourseOffering(course,
                new CreateOfferingRequest("2026/2027", "Računarstvo", "OSNOVNE", (short) 1, (short) 1)));
        lecture = groups.saveAndFlush(new TeachingGroup(offering, ActivityType.LECTURE, "ALL"));
        student = new Student("100/26", "Marija", "Stojanović", "marija.stojanovic@student.etf.unibl.org");
        student.linkIdentity("https://accounts.google.com", "google-student-1");
        student = students.saveAndFlush(student);
    }

    @Test
    void recordsAttendanceUsingAuthenticatedStudentAndServerTime() throws Exception {
        enrollments.saveAndFlush(new Enrollment(student, offering, "REDOVAN", Set.of()));
        TeachingSession session = activeSession(lecture);

        mvc.perform(post("/api/student/attendance").with(studentJwt()).with(request -> {
                            request.setRemoteAddr("203.0.113.25");
                            request.addHeader("User-Agent", "Attendance Test Browser/1.0");
                            return request;
                        }).contentType("application/json")
                        .content(body(qr.current(session.getId()).token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("sessionId").value(session.getId().toString()))
                .andExpect(jsonPath("studentId").value(student.getId().toString()))
                .andExpect(jsonPath("courseCode").value("ATT-1"))
                .andExpect(jsonPath("courseName").value("Evidencija prisustva"))
                .andExpect(jsonPath("activityType").value("LECTURE"))
                .andExpect(jsonPath("groupNumber").value("ALL"))
                .andExpect(jsonPath("recordedAt").value(start.toString()))
                .andExpect(jsonPath("ipAddress").value("203.0.113.25"))
                .andExpect(jsonPath("userAgent").value("Attendance Test Browser/1.0"));

        Attendance saved = attendances.findAll().getFirst();
        assertThat(saved.getStudent().getId()).isEqualTo(student.getId());
        assertThat(saved.getSession().getId()).isEqualTo(session.getId());
        assertThat(saved.getRecordedAt()).isEqualTo(start);
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.25");
        assertThat(saved.getUserAgent()).isEqualTo("Attendance Test Browser/1.0");
    }

    @Test
    void employeeCanReviewAndDeleteAttendance() throws Exception {
        enrollments.saveAndFlush(new Enrollment(student, offering, "REDOVAN", Set.of()));
        TeachingSession session = activeSession(lecture);
        mvc.perform(post("/api/student/attendance").with(studentJwt()).with(request -> {
                            request.setRemoteAddr("2001:db8::10");
                            request.addHeader("User-Agent", "Mobile Browser/2.0");
                            return request;
                        }).contentType("application/json")
                        .content(body(qr.current(session.getId()).token())))
                .andExpect(status().isOk());
        UUID attendanceId = attendances.findAll().getFirst().getId();

        mvc.perform(get("/api/sessions/{sessionId}/attendances", session.getId()).with(employeeJwt(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(attendanceId.toString()))
                .andExpect(jsonPath("$[0].ipAddress").value("2001:db8::10"))
                .andExpect(jsonPath("$[0].userAgent").value("Mobile Browser/2.0"));
        mvc.perform(delete("/api/sessions/{sessionId}/attendances/{attendanceId}",
                        session.getId(), attendanceId).with(employeeJwt(employee)))
                .andExpect(status().isNoContent());
        assertThat(attendances.count()).isZero();
    }

    @Test
    void employeeCannotReviewOrDeleteAttendanceFromUnassignedCourse() throws Exception {
        enrollments.saveAndFlush(new Enrollment(student, offering, "REDOVAN", Set.of()));
        TeachingSession session = activeSession(lecture);
        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json")
                        .content(body(qr.current(session.getId()).token())))
                .andExpect(status().isOk());
        UUID attendanceId = attendances.findAll().getFirst().getId();
        LocalUser other = localUsers.saveAndFlush(new LocalUser("other-professor", "hash",
                "other.professor@etf.unibl.org", "EMPLOYEE"));

        mvc.perform(get("/api/sessions/{sessionId}/attendances", session.getId()).with(employeeJwt(other)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/sessions/{sessionId}/attendances/{attendanceId}",
                        session.getId(), attendanceId).with(employeeJwt(other)))
                .andExpect(status().isForbidden());
        assertThat(attendances.count()).isOne();
    }

    @Test
    void employeeCannotDeleteAttendanceUsingDifferentSessionId() throws Exception {
        enrollments.saveAndFlush(new Enrollment(student, offering, "REDOVAN", Set.of()));
        TeachingSession recordedSession = activeSession(lecture);
        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json")
                        .content(body(qr.current(recordedSession.getId()).token())))
                .andExpect(status().isOk());
        UUID attendanceId = attendances.findAll().getFirst().getId();
        TeachingSession otherSession = activeSession(lecture);

        mvc.perform(delete("/api/sessions/{sessionId}/attendances/{attendanceId}",
                        otherSession.getId(), attendanceId).with(employeeJwt(employee)))
                .andExpect(status().isNotFound());
        assertThat(attendances.count()).isOne();
    }

    @Test
    void recordsAttendanceUsingCurrentPin() throws Exception {
        enrollments.saveAndFlush(new Enrollment(student, offering, "REDOVAN", Set.of()));
        TeachingSession session = activeSession(lecture);
        String pin = qr.current(session.getId()).pin();

        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("pin", pin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("sessionId").value(session.getId().toString()))
                .andExpect(jsonPath("studentId").value(student.getId().toString()));
        assertThat(attendances.count()).isOne();
    }

    @Test
    void preventsRepeatedAttendanceForSameSession() throws Exception {
        enrollments.saveAndFlush(new Enrollment(student, offering, "REDOVAN", Set.of()));
        TeachingSession session = activeSession(lecture);
        String body = body(qr.current(session.getId()).token());

        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json").content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("detail").value("Prisustvo studenta je već evidentirano za ovu sesiju."));
        assertThat(attendances.count()).isOne();
    }

    @Test
    void rejectsPreviousQrAndPinAfterRotation() throws Exception {
        enrollments.saveAndFlush(new Enrollment(student, offering, "REDOVAN", Set.of()));
        TeachingSession session = activeSession(lecture);
        var previous = qr.current(session.getId());
        when(qrClock.instant()).thenReturn(start.plusSeconds(10));
        var current = qr.current(session.getId());

        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json")
                        .content(body(previous.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("detail").value("QR token nije važeći."));
        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("pin", previous.pin()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("detail").value("PIN kod nije važeći."));
        assertThat(attendances.count()).isZero();

        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json")
                        .content(body(current.token())))
                .andExpect(status().isOk());
        assertThat(attendances.count()).isOne();
    }

    @Test
    void concurrentAttemptsBySameStudentCreateOneAttendance() throws Exception {
        enrollments.saveAndFlush(new Enrollment(student, offering, "REDOVAN", Set.of()));
        TeachingSession session = activeSession(lecture);
        String payload = body(qr.current(session.getId()).token());
        List<Integer> statuses = concurrentRequests(8, number ->
                mvc.perform(post("/api/student/attendance").with(studentJwt())
                        .contentType("application/json").content(payload))
                        .andReturn().getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(200, 409, 409, 409, 409, 409, 409, 409);
        assertThat(attendances.count()).isOne();
    }

    @Test
    void concurrentAttemptsByDifferentStudentsKeepEveryAttendance() throws Exception {
        int numberOfStudents = 50;
        for (int number = 1; number < numberOfStudents; number++) {
            Student other = new Student("LOAD-" + number + "/26", "Student", "Test" + number,
                    "student" + number + "@student.etf.unibl.org");
            other.linkIdentity("https://accounts.google.com", "google-student-" + (number + 1));
            enrollments.saveAndFlush(new Enrollment(students.saveAndFlush(other), offering, "REDOVAN", Set.of()));
        }
        enrollments.saveAndFlush(new Enrollment(student, offering, "REDOVAN", Set.of()));
        TeachingSession session = activeSession(lecture);
        String payload = body(qr.current(session.getId()).token());
        List<Integer> statuses = concurrentRequests(numberOfStudents, number ->
                mvc.perform(post("/api/student/attendance").with(studentJwt("google-student-" + (number + 1)))
                        .contentType("application/json").content(payload))
                        .andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(200).hasSize(numberOfStudents);
        assertThat(attendances.count()).isEqualTo(numberOfStudents);
        assertThat(attendances.findAll()).extracting(item -> item.getStudent().getId()).doesNotHaveDuplicates();
    }

    @Test
    void databaseRejectsDuplicateStudentAndSession() throws Exception {
        enrollments.saveAndFlush(new Enrollment(student, offering, "REDOVAN", Set.of()));
        TeachingSession session = activeSession(lecture);
        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json")
                        .content(body(qr.current(session.getId()).token())))
                .andExpect(status().isOk());

        assertThatThrownBy(() -> jdbc.update("""
                insert into attendances (id, teaching_session_id, student_id, recorded_at, ip_address, user_agent)
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), session.getId(), student.getId(),
                java.sql.Timestamp.from(start), "203.0.113.10", "Test Browser"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(attendances.count()).isOne();
    }

    @Test
    void rejectsStudentWhoIsNotEnrolledInSessionOffering() throws Exception {
        TeachingSession session = activeSession(lecture);
        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json")
                        .content(body(qr.current(session.getId()).token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("detail").value("Student nije upisan na predmet ove sesije."));
        assertThat(attendances.count()).isZero();
    }

    @Test
    void checksGroupForExercises() throws Exception {
        TeachingGroup first = groups.saveAndFlush(new TeachingGroup(offering, ActivityType.AUDITORY_EXERCISE, "G1"));
        TeachingGroup second = groups.saveAndFlush(new TeachingGroup(offering, ActivityType.AUDITORY_EXERCISE, "G2"));
        enrollments.saveAndFlush(new Enrollment(student, offering, "REDOVAN", Set.of(first)));
        TeachingSession session = activeSession(second);

        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json")
                        .content(body(qr.current(session.getId()).token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("detail").value("Student ne pripada grupi izabranoj za ovu sesiju."));
    }

    @Test
    void acceptsExerciseSessionForStudentsGroup() throws Exception {
        TeachingGroup group = groups.saveAndFlush(new TeachingGroup(offering, ActivityType.LABORATORY_EXERCISE, "L1"));
        enrollments.saveAndFlush(new Enrollment(student, offering, "REDOVAN", Set.of(group)));
        TeachingSession session = activeSession(group);

        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json")
                        .content(body(qr.current(session.getId()).token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("groupNumber").value("L1"));
    }

    @Test
    void rejectsExpiredTokenAndClosedSession() throws Exception {
        enrollments.saveAndFlush(new Enrollment(student, offering, "REDOVAN", Set.of()));
        TeachingSession expired = activeSession(lecture);
        String expiredToken = qr.current(expired.getId()).token();
        when(qrClock.instant()).thenReturn(start.plusSeconds(10));
        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json")
                        .content(body(expiredToken)))
                .andExpect(status().isBadRequest());

        when(qrClock.instant()).thenReturn(start);
        TeachingSession closed = activeSession(lecture);
        String closedToken = qr.current(closed.getId()).token();
        sessionService.close(closed.getId());
        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json")
                        .content(body(closedToken)))
                .andExpect(status().isConflict());
        assertThat(attendances.count()).isZero();
    }

    @Test
    void rejectsUnknownIdentityMalformedRequestEmployeeAndAnonymousAccess() throws Exception {
        enrollments.saveAndFlush(new Enrollment(student, offering, "REDOVAN", Set.of()));
        TeachingSession session = activeSession(lecture);
        String body = body(qr.current(session.getId()).token());
        var unknown = jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).jwt(value -> value.claim("role", "STUDENT")
                .claim("identity_issuer", "https://accounts.google.com")
                .claim("identity_subject", "unknown"));

        mvc.perform(post("/api/student/attendance").with(unknown).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json")
                        .content("{\"token\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/student/attendance").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
                        .jwt(value -> value.claim("role", "EMPLOYEE")))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/student/attendance").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
    }

    private TeachingSession activeSession(TeachingGroup group) {
        TeachingSession session = sessions.saveAndFlush(
                new TeachingSession(group, LocalDate.of(2026, 10, 1), LocalTime.of(10, 0)));
        sessionService.activate(session.getId());
        return sessions.findById(session.getId()).orElseThrow();
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor studentJwt() {
        return studentJwt("google-student-1");
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor studentJwt(String subject) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).jwt(value -> value.claim("role", "STUDENT")
                .claim("identity_issuer", "https://accounts.google.com")
                .claim("identity_subject", subject));
    }

    private List<Integer> concurrentRequests(int count, AttendanceRequestAction request)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch startSignal = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int number = 0; number < count; number++) {
                final int studentNumber = number;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!startSignal.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Zahtjevi nisu spremni.");
                    return request.send(studentNumber);
                }));
            }
            if (!ready.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Zahtjevi nisu spremni.");
            startSignal.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) statuses.add(future.get(90, TimeUnit.SECONDS));
            return statuses;
        }
    }

    @FunctionalInterface
    private interface AttendanceRequestAction {
        int send(int number) throws Exception;
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor employeeJwt(LocalUser user) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")).jwt(value ->
                value.subject(user.getId().toString()).claim("role", "EMPLOYEE").claim("email", user.getEmail()));
    }

    private String body(String token) throws Exception {
        return json.writeValueAsString(Map.of("token", token));
    }
}
