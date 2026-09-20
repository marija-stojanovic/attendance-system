package com.attendance.course.attendance;

import com.attendance.course.*;
import com.attendance.course.sessions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @MockitoBean private Clock qrClock;

    private final Instant start = Instant.parse("2026-10-01T09:00:00Z");
    private Student student;
    private CourseOffering offering;
    private TeachingGroup lecture;

    @BeforeEach
    void setUp() {
        when(qrClock.instant()).thenReturn(start);
        attendances.deleteAll();
        sessions.deleteAll();
        enrollments.deleteAll();
        students.deleteAll();
        groups.deleteAll();
        offerings.deleteAll();
        courses.deleteAll();
        Course course = courses.saveAndFlush(new Course("ATT-1", "Evidencija prisustva"));
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

        mvc.perform(post("/api/student/attendance").with(studentJwt()).contentType("application/json")
                        .content(body(qr.current(session.getId()).token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("sessionId").value(session.getId().toString()))
                .andExpect(jsonPath("studentId").value(student.getId().toString()))
                .andExpect(jsonPath("courseCode").value("ATT-1"))
                .andExpect(jsonPath("courseName").value("Evidencija prisustva"))
                .andExpect(jsonPath("activityType").value("LECTURE"))
                .andExpect(jsonPath("groupNumber").value("ALL"))
                .andExpect(jsonPath("recordedAt").value(start.toString()));

        Attendance saved = attendances.findAll().getFirst();
        assertThat(saved.getStudent().getId()).isEqualTo(student.getId());
        assertThat(saved.getSession().getId()).isEqualTo(session.getId());
        assertThat(saved.getRecordedAt()).isEqualTo(start);
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
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).jwt(value -> value.claim("role", "STUDENT")
                .claim("identity_issuer", "https://accounts.google.com")
                .claim("identity_subject", "google-student-1")
                .claim("email", "marija.stojanovic@student.etf.unibl.org"));
    }

    private String body(String token) throws Exception {
        return json.writeValueAsString(Map.of("token", token));
    }
}
