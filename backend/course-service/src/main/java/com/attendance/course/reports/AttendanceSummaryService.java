package com.attendance.course.reports;

import com.attendance.course.ActivityType;
import com.attendance.course.Enrollment;
import com.attendance.course.EnrollmentRepository;
import com.attendance.course.attendance.AttendanceRepository;
import com.attendance.course.sessions.SessionStatus;
import com.attendance.course.sessions.TeachingSession;
import com.attendance.course.sessions.TeachingSessionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AttendanceSummaryService {
    private final AttendanceReportDataService reports;
    private final EnrollmentRepository enrollments;
    private final TeachingSessionRepository sessions;
    private final AttendanceRepository attendances;

    public AttendanceSummaryService(AttendanceReportDataService reports, EnrollmentRepository enrollments,
            TeachingSessionRepository sessions, AttendanceRepository attendances) {
        this.reports = reports;
        this.enrollments = enrollments;
        this.sessions = sessions;
        this.attendances = attendances;
    }

    public ReportData.Summary summary(UUID offeringId, UUID employeeId) {
        var offering = reports.offering(offeringId, employeeId);
        var closedSessions = sessions.findByGroupOfferingIdAndStatus(offeringId, SessionStatus.CLOSED).stream()
                .sorted(Comparator.comparing(TeachingSession::getDate)
                        .thenComparing(TeachingSession::getStartTime)
                        .thenComparing(TeachingSession::getId))
                .toList();
        var courseEnrollments = enrollments.findForAttendanceSummary(offeringId);
        Map<UUID, Set<UUID>> sessionsByStudent = new HashMap<>();
        if (!closedSessions.isEmpty()) {
            var ids = closedSessions.stream().map(session -> session.getId()).toList();
            for (var pair : attendances.findPairsBySessionIds(ids)) {
                sessionsByStudent.computeIfAbsent(pair.getStudentId(), ignored -> new HashSet<>())
                        .add(pair.getSessionId());
            }
        }

        var students = courseEnrollments.stream().map(enrollment -> {
            var student = enrollment.getStudent();
            Set<UUID> attendedIds = sessionsByStudent.getOrDefault(student.getId(), Set.of());
            int expected = 0;
            int arrivals = 0;
            for (var session : closedSessions) {
                if (applies(enrollment, session, attendedIds)) {
                    expected++;
                    if (attendedIds.contains(session.getId())) arrivals++;
                }
            }
            return new ReportData.StudentSummary(new ReportData.Student(student.getId(), student.getFirstName(),
                    student.getLastName(), student.getIndexNumber(), student.getInstitutionalEmail()),
                    arrivals, expected - arrivals, expected, percentage(arrivals, expected));
        }).sorted(Comparator.comparingInt(ReportData.StudentSummary::arrivals).reversed()
                .thenComparing(item -> item.student().lastName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> item.student().firstName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> item.student().indexNumber()))
                .toList();
        var activityGroups = closedSessions.stream()
                .map(session -> new ReportData.ActivityGroup(session.getActivityType(),
                        session.getGroup().getGroupNumber()))
                .distinct()
                .sorted(Comparator.comparing(ReportData.ActivityGroup::activityType)
                        .thenComparing(ReportData.ActivityGroup::groupNumber))
                .toList();
        return new ReportData.Summary(offering, closedSessions.size(), activityGroups, students);
    }

    private boolean applies(Enrollment enrollment, TeachingSession session, Set<UUID> attendedSessionIds) {
        return session.getActivityType() == ActivityType.LECTURE
                || enrollment.getGroups().stream().anyMatch(group -> group.getId().equals(session.getGroup().getId()))
                || attendedSessionIds.contains(session.getId());
    }

    private BigDecimal percentage(int value, int total) {
        if (total == 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
