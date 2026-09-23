package com.attendance.course.reports;

import com.attendance.course.EnrollmentRepository;
import com.attendance.course.attendance.AttendanceRepository;
import com.attendance.course.sessions.SessionStatus;
import com.attendance.course.sessions.TeachingSession;
import com.attendance.course.sessions.TeachingSessionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsSourceService {
    private final AttendanceReportDataService reports;
    private final EnrollmentRepository enrollments;
    private final TeachingSessionRepository sessions;
    private final AttendanceRepository attendances;

    public AnalyticsSourceService(AttendanceReportDataService reports, EnrollmentRepository enrollments,
            TeachingSessionRepository sessions, AttendanceRepository attendances) {
        this.reports = reports;
        this.enrollments = enrollments;
        this.sessions = sessions;
        this.attendances = attendances;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AnalyticsSourceData load(UUID offeringId, UUID employeeId) {
        reports.offering(offeringId, employeeId);
        var enrolled = enrollments.findForAttendanceSummary(offeringId).stream().map(enrollment -> {
            var student = enrollment.getStudent();
            var groupIds = enrollment.getGroups().stream().map(group -> group.getId())
                    .sorted().toList();
            return new AnalyticsSourceData.Enrollment(new AnalyticsSourceData.Student(student.getId(),
                    student.getFirstName(), student.getLastName(), student.getIndexNumber()), groupIds);
        }).sorted(Comparator.comparing(item -> item.student().indexNumber())).toList();
        var closed = sessions.findByGroupOfferingIdAndStatus(offeringId, SessionStatus.CLOSED).stream()
                .sorted(Comparator.comparing(TeachingSession::getDate)
                        .thenComparing(TeachingSession::getStartTime)
                        .thenComparing(TeachingSession::getId))
                .toList();
        var sessionData = closed.stream().map(session -> new AnalyticsSourceData.Session(session.getId(),
                session.getDate(), session.getStartTime(), session.getActivityType(),
                session.getGroup().getId(), session.getGroup().getGroupNumber())).toList();
        List<AnalyticsSourceData.Attendance> attendanceData = closed.isEmpty() ? List.of()
                : attendances.findPairsBySessionIds(closed.stream().map(TeachingSession::getId).toList())
                        .stream().map(pair -> new AnalyticsSourceData.Attendance(pair.getSessionId(),
                                pair.getStudentId()))
                        .sorted(Comparator.comparing(AnalyticsSourceData.Attendance::sessionId)
                                .thenComparing(AnalyticsSourceData.Attendance::studentId))
                        .toList();
        return new AnalyticsSourceData(enrolled, sessionData, attendanceData);
    }
}
