package com.attendance.course.reports;

import com.attendance.course.ActivityType;
import com.attendance.course.Course;
import com.attendance.course.CourseEmployee;
import com.attendance.course.CourseEmployeeRepository;
import com.attendance.course.CourseEmployeeRole;
import com.attendance.course.CourseOffering;
import com.attendance.course.CourseOfferingRepository;
import com.attendance.course.CourseRepository;
import com.attendance.course.CreateOfferingRequest;
import com.attendance.course.Enrollment;
import com.attendance.course.EnrollmentRepository;
import com.attendance.course.Student;
import com.attendance.course.StudentRepository;
import com.attendance.course.TeachingGroup;
import com.attendance.course.TeachingGroupRepository;
import com.attendance.course.attendance.Attendance;
import com.attendance.course.attendance.AttendanceRepository;
import com.attendance.course.security.LocalUser;
import com.attendance.course.security.LocalUserRepository;
import com.attendance.course.sessions.TeachingSession;
import com.attendance.course.sessions.TeachingSessionRepository;
import com.attendance.course.sessions.TeachingSessionService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsSourceControllerTests {
    @Autowired MockMvc mvc;
    @Autowired LocalUserRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseEmployeeRepository courseEmployees;
    @Autowired CourseOfferingRepository offerings;
    @Autowired TeachingGroupRepository groups;
    @Autowired StudentRepository students;
    @Autowired EnrollmentRepository enrollments;
    @Autowired TeachingSessionRepository sessions;
    @Autowired TeachingSessionService sessionService;
    @Autowired AttendanceRepository attendances;

    @Test
    void returnsOnlyCourseDataToAssignedEmployee() throws Exception {
        LocalUser professor = users.saveAndFlush(new LocalUser("analytics-professor", "hash",
                "analytics-professor@etf.org", "EMPLOYEE"));
        Course course = courses.saveAndFlush(new Course("ANL-101", "Analitika"));
        courseEmployees.saveAndFlush(new CourseEmployee(course, professor, CourseEmployeeRole.TEACHER));
        CourseOffering offering = offerings.saveAndFlush(new CourseOffering(course,
                new CreateOfferingRequest("2026/2027", "Računarstvo", "Osnovne", (short) 2, (short) 3)));
        TeachingGroup group = groups.saveAndFlush(new TeachingGroup(offering, ActivityType.AUDITORY_EXERCISE, "G1"));
        Student student = students.saveAndFlush(new Student("101/25", "Marija", "Stojanović",
                "analytics-marija@student.etf.unibl.org"));
        enrollments.saveAndFlush(new Enrollment(student, offering, "R", Set.of(group)));
        TeachingSession closed = sessions.saveAndFlush(new TeachingSession(group,
                LocalDate.of(2026, 10, 1), LocalTime.of(9, 0)));
        sessions.saveAndFlush(new TeachingSession(group, LocalDate.of(2026, 10, 2), LocalTime.of(9, 0)));
        sessionService.activate(closed.getId());
        sessionService.close(closed.getId());
        attendances.saveAndFlush(new Attendance(closed, student, Instant.now(), "127.0.0.1",
                "test", UUID.randomUUID()));
        String path = "/api/offerings/" + offering.getId() + "/analytics-source";

        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject(student.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject(professor.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollments.length()").value(1))
                .andExpect(jsonPath("$.enrollments[0].student.indexNumber").value("101/25"))
                .andExpect(jsonPath("$.enrollments[0].groupIds[0]").value(group.getId().toString()))
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].id").value(closed.getId().toString()))
                .andExpect(jsonPath("$.sessions[0].groupId").value(group.getId().toString()))
                .andExpect(jsonPath("$.attendances.length()").value(1))
                .andExpect(jsonPath("$.attendances[0].studentId").value(student.getId().toString()));
    }
}
