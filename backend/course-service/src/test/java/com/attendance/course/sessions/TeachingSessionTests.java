package com.attendance.course.sessions;

import com.attendance.course.ActivityType;
import com.attendance.course.Course;
import com.attendance.course.CourseOffering;
import com.attendance.course.CreateOfferingRequest;
import com.attendance.course.TeachingGroup;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = "spring.datasource.url=${SESSION_TEST_DB_URL:jdbc:h2:mem:session_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TeachingSessionTests {
    @Autowired private TestEntityManager entityManager;
    @Autowired private TeachingSessionRepository sessions;

    private CourseOffering offering;

    @BeforeEach
    void setUp() {
        Course course = entityManager.persist(new Course("SESSION-101", "Testni predmet"));
        offering = entityManager.persist(new CourseOffering(course,
                new CreateOfferingRequest("2026/2027", "Testni program", "OSNOVNE", (short) 1, (short) 1)));
    }

    @ParameterizedTest
    @EnumSource(ActivityType.class)
    void savesSessionWithCorrectCourseGroupAndActivity(ActivityType activity) {
        TeachingGroup group = entityManager.persist(new TeachingGroup(offering, activity, "1"));
        TeachingSession session = sessions.saveAndFlush(new TeachingSession(group,
                LocalDate.of(2026, 10, 1), LocalTime.of(9, 30)));
        var id = session.getId();
        var courseId = offering.getCourse().getId();
        entityManager.clear();

        TeachingSession saved = sessions.findById(id).orElseThrow();
        assertThat(saved.getCourse().getId()).isEqualTo(courseId);
        assertThat(saved.getGroup().getId()).isEqualTo(group.getId());
        assertThat(saved.getActivityType()).isEqualTo(activity);
        assertThat(saved.getDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.CREATED);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsMissingDate() {
        TeachingGroup group = entityManager.persist(new TeachingGroup(offering, ActivityType.LECTURE, "1"));
        assertThatThrownBy(() -> sessions.saveAndFlush(new TeachingSession(group, null, LocalTime.NOON)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsMissingStartTime() {
        TeachingGroup group = entityManager.persist(new TeachingGroup(offering, ActivityType.LECTURE, "1"));
        assertThatThrownBy(() -> sessions.saveAndFlush(new TeachingSession(group, LocalDate.of(2026, 10, 1), null)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsMissingGroup() {
        assertThatThrownBy(() -> sessions.saveAndFlush(new TeachingSession(null, LocalDate.of(2026, 10, 1), LocalTime.NOON)))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
