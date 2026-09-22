package com.attendance.course.reports;

import com.attendance.course.ActivityType;
import com.attendance.course.EnrollmentRepository;
import com.attendance.course.attendance.AttendanceRepository;
import com.attendance.course.sessions.SessionStatus;
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
        var closedSessions = sessions.findByGroupOfferingIdAndStatus(offeringId, SessionStatus.CLOSED);
        Map<UUID, Set<UUID>> attended = new HashMap<>();
        if (!closedSessions.isEmpty()) {
            var ids = closedSessions.stream().map(session -> session.getId()).toList();
            for (var pair : attendances.findPairsBySessionIds(ids)) {
                attended.computeIfAbsent(pair.getStudentId(), ignored -> new HashSet<>()).add(pair.getSessionId());
            }
        }

        var students = enrollments.findForAttendanceSummary(offeringId).stream().map(enrollment -> {
            var student = enrollment.getStudent();
            Set<UUID> groupIds = enrollment.getGroups().stream().map(group -> group.getId())
                    .collect(java.util.stream.Collectors.toSet());
            Set<UUID> attendedIds = attended.getOrDefault(student.getId(), Set.of());
            int expected = 0;
            int arrivals = 0;
            for (var session : closedSessions) {
                boolean applies = session.getActivityType() == ActivityType.LECTURE
                        || groupIds.contains(session.getGroup().getId())
                        || attendedIds.contains(session.getId());
                if (applies) {
                    expected++;
                    if (attendedIds.contains(session.getId())) arrivals++;
                }
            }
            BigDecimal percentage = expected == 0 ? BigDecimal.ZERO.setScale(2)
                    : BigDecimal.valueOf(arrivals).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(expected), 2, RoundingMode.HALF_UP);
            return new ReportData.StudentSummary(new ReportData.Student(student.getId(), student.getFirstName(),
                    student.getLastName(), student.getIndexNumber(), student.getInstitutionalEmail()),
                    arrivals, expected - arrivals, expected, percentage);
        }).sorted(Comparator.comparingInt(ReportData.StudentSummary::arrivals).reversed()
                .thenComparing(item -> item.student().lastName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> item.student().firstName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> item.student().indexNumber()))
                .toList();
        return new ReportData.Summary(offering, closedSessions.size(), students);
    }
}
