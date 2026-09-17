package com.attendance.course.sessions;

import com.attendance.course.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:session_api_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "EMPLOYEE")
class TeachingSessionControllerTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TeachingSessionRepository sessions;
    @Autowired private CourseRepository courses;
    @Autowired private CourseOfferingRepository offerings;
    @Autowired private TeachingGroupRepository groups;
    @Autowired private TeachingSessionService service;

    private Course course;
    private TeachingGroup group;

    @BeforeEach
    void setUp() {
        sessions.deleteAll();
        groups.deleteAll();
        offerings.deleteAll();
        courses.deleteAll();
        course = courses.saveAndFlush(new Course("SES-1", "Programiranje"));
        group = createGroup(course, ActivityType.LECTURE);
    }

    @ParameterizedTest
    @EnumSource(ActivityType.class)
    void createsAndReadsSession(ActivityType activity) throws Exception {
        TeachingGroup selected = activity == ActivityType.LECTURE ? group : createGroup(course, activity);
        var result = mvc.perform(post("/api/courses/{id}/sessions", course.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body(selected.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("courseId").value(course.getId().toString()))
                .andExpect(jsonPath("offeringId").value(selected.getOffering().getId().toString()))
                .andExpect(jsonPath("groupId").value(selected.getId().toString()))
                .andExpect(jsonPath("groupNumber").value("1"))
                .andExpect(jsonPath("activityType").value(activity.name()))
                .andExpect(jsonPath("status").value("CREATED"))
                .andExpect(jsonPath("date").value("2026-10-01"))
                .andExpect(jsonPath("startTime").value("09:30:00"))
                .andExpect(jsonPath("createdAt").isNotEmpty()).andReturn();
        String id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/sessions/" + id);
        mvc.perform(get("/api/sessions/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("id").value(id));
        assertThat(sessions.count()).isEqualTo(1);
    }

    @Test
    void listsOnlyRequestedCourseInDateAndTimeOrder() throws Exception {
        saveSession(group, "2026-10-02", "08:00");
        TeachingSession later = saveSession(group, "2026-10-01", "12:00");
        TeachingSession first = saveSession(group, "2026-10-01", "09:30");
        Course other = courses.saveAndFlush(new Course("SES-2", "Matematika"));
        saveSession(createGroup(other, ActivityType.LECTURE), "2026-09-01", "08:00");
        mvc.perform(get("/api/courses/{id}/sessions", course.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(first.getId().toString()))
                .andExpect(jsonPath("$[1].id").value(later.getId().toString()));
    }

    @Test
    void returnsEmptyListForCourseWithoutSessions() throws Exception {
        mvc.perform(get("/api/courses/{id}/sessions", course.getId()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void updatesCreatedSessionAndPersistsLifecycle() throws Exception {
        UUID id = saveSession(group, "2026-09-01", "08:00").getId();
        TeachingGroup lab = createGroup(course, ActivityType.LABORATORY_EXERCISE);
        mvc.perform(put("/api/sessions/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body(lab.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("groupId").value(lab.getId().toString()))
                .andExpect(jsonPath("activityType").value("LABORATORY_EXERCISE"));
        mvc.perform(get("/api/sessions/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("date").value("2026-10-01"))
                .andExpect(jsonPath("startTime").value("09:30:00"));
        mvc.perform(post("/api/sessions/{id}/activate", id)).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("ACTIVE"));
        assertThat(sessions.findById(id).orElseThrow().getStatus()).isEqualTo(SessionStatus.ACTIVE);
        mvc.perform(post("/api/sessions/{id}/close", id)).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("CLOSED"));
        assertThat(sessions.findById(id).orElseThrow().getStatus()).isEqualTo(SessionStatus.CLOSED);
    }

    @ParameterizedTest
    @EnumSource(SessionStatus.class)
    void rejectsInvalidTransitionsAndChanges(SessionStatus state) throws Exception {
        UUID id = saveSession(group, "2026-10-01", "09:30").getId();
        if (state != SessionStatus.CREATED) {
            mvc.perform(post("/api/sessions/{id}/activate", id)).andExpect(status().isOk());
        }
        if (state == SessionStatus.CLOSED) {
            mvc.perform(post("/api/sessions/{id}/close", id)).andExpect(status().isOk());
        }
        if (state != SessionStatus.ACTIVE) {
            mvc.perform(post("/api/sessions/{id}/close", id)).andExpect(status().isConflict());
        }
        if (state != SessionStatus.CREATED) {
            mvc.perform(post("/api/sessions/{id}/activate", id)).andExpect(status().isConflict());
            mvc.perform(put("/api/sessions/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body(group.getId())))
                    .andExpect(status().isConflict());
        }
        assertThat(sessions.findById(id).orElseThrow().getStatus()).isEqualTo(state);
    }

    @ParameterizedTest
    @ValueSource(strings = {"groupId", "date", "startTime"})
    void rejectsMissingFieldsOnCreateAndUpdate(String field) throws Exception {
        var request = mapper.readTree(body(group.getId()));
        ((com.fasterxml.jackson.databind.node.ObjectNode) request).remove(field);
        mvc.perform(post("/api/courses/{id}/sessions", course.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("errors." + field).isNotEmpty());
        assertThat(sessions.count()).isZero();
        UUID id = saveSession(group, "2026-09-01", "08:00").getId();
        mvc.perform(put("/api/sessions/{id}", id).contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest());
        assertThat(sessions.findById(id).orElseThrow().getDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"date\":\"2026-99-99\"}", "{\"startTime\":\"25:00\"}", "{\"groupId\":\"wrong\"}"})
    void rejectsMalformedInput(String request) throws Exception {
        mvc.perform(post("/api/courses/{id}/sessions", course.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
        assertThat(sessions.count()).isZero();
    }

    @Test
    void rejectsGroupFromAnotherCourseWithoutChangingSession() throws Exception {
        Course other = courses.saveAndFlush(new Course("SES-2", "Matematika"));
        TeachingGroup foreignGroup = createGroup(other, ActivityType.LECTURE);
        mvc.perform(post("/api/courses/{id}/sessions", course.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body(foreignGroup.getId())))
                .andExpect(status().isBadRequest());
        assertThat(sessions.count()).isZero();
        UUID id = saveSession(group, "2026-09-01", "08:00").getId();
        mvc.perform(put("/api/sessions/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body(foreignGroup.getId())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/sessions/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("groupId").value(group.getId().toString()))
                .andExpect(jsonPath("date").value("2026-09-01"));
    }

    @Test
    void returnsNotFoundForMissingResources() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(get("/api/courses/{id}/sessions", missing)).andExpect(status().isNotFound());
        mvc.perform(post("/api/courses/{id}/sessions", missing)
                .contentType(MediaType.APPLICATION_JSON).content(body(group.getId()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/courses/{id}/sessions", course.getId())
                .contentType(MediaType.APPLICATION_JSON).content(body(missing))).andExpect(status().isNotFound());
        mvc.perform(get("/api/sessions/{id}", missing)).andExpect(status().isNotFound());
        mvc.perform(put("/api/sessions/{id}", missing)
                .contentType(MediaType.APPLICATION_JSON).content(body(group.getId()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/sessions/{id}/activate", missing)).andExpect(status().isNotFound());
        mvc.perform(post("/api/sessions/{id}/close", missing)).andExpect(status().isNotFound());
    }

    @Test
    void allowsOnlyOneConcurrentActivation() throws Exception {
        UUID id = saveSession(group, "2026-10-01", "09:30").getId();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Integer> activate = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Zahtjevi nisu pokrenuti na vrijeme.");
            }
            try {
                service.activate(id);
                return 200;
            } catch (ResponseStatusException exception) {
                return exception.getStatusCode().value();
            }
        };
        try {
            var first = executor.submit(activate);
            var second = executor.submit(activate);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(sessions.findById(id).orElseThrow().getStatus()).isEqualTo(SessionStatus.ACTIVE);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void deniesStudentAccess() throws Exception {
        assertAccessDenied(403);
    }

    @Test
    @WithAnonymousUser
    void requiresAuthentication() throws Exception {
        assertAccessDenied(401);
    }

    private void assertAccessDenied(int code) throws Exception {
        UUID id = saveSession(group, "2026-10-01", "09:30").getId();
        mvc.perform(get("/api/courses/{id}/sessions", course.getId())).andExpect(status().is(code));
        mvc.perform(get("/api/sessions/{id}", id)).andExpect(status().is(code));
        mvc.perform(post("/api/courses/{id}/sessions", course.getId())
                .contentType(MediaType.APPLICATION_JSON).content(body(group.getId()))).andExpect(status().is(code));
        mvc.perform(put("/api/sessions/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content(body(group.getId()))).andExpect(status().is(code));
        mvc.perform(post("/api/sessions/{id}/activate", id)).andExpect(status().is(code));
        mvc.perform(post("/api/sessions/{id}/close", id)).andExpect(status().is(code));
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(sessions.findById(id).orElseThrow().getStatus()).isEqualTo(SessionStatus.CREATED);
    }

    private TeachingGroup createGroup(Course owner, ActivityType activity) {
        CourseOffering offering = offerings.findByCourseIdOrderByAcademicYearDescStudyProgramAscStudyTypeAsc(owner.getId())
                .stream().findFirst().orElseGet(() -> offerings.saveAndFlush(new CourseOffering(owner,
                        new CreateOfferingRequest("2026/2027", "Računarstvo", "OSNOVNE", (short) 1, (short) 1))));
        return groups.saveAndFlush(new TeachingGroup(offering, activity, "1"));
    }

    private TeachingSession saveSession(TeachingGroup selected, String date, String time) {
        return sessions.saveAndFlush(new TeachingSession(selected, LocalDate.parse(date), LocalTime.parse(time)));
    }

    private String body(UUID groupId) throws Exception {
        return mapper.writeValueAsString(Map.of("groupId", groupId, "date", "2026-10-01", "startTime", "09:30"));
    }
}
