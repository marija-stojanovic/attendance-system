package com.attendance.course.attendance;

import com.attendance.course.*;
import com.attendance.course.sessions.SessionQrService;
import com.attendance.course.sessions.SessionStatus;
import com.attendance.course.sessions.TeachingSession;
import com.attendance.course.sessions.TeachingSessionRepository;
import java.util.UUID;
import java.util.List;
import com.attendance.course.CourseEmployeeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttendanceService {
    private final AttendanceRepository attendances;
    private final TeachingSessionRepository sessions;
    private final StudentRepository students;
    private final EnrollmentRepository enrollments;
    private final SessionQrService qr;
    private final CourseEmployeeRepository courseEmployees;

    public AttendanceService(AttendanceRepository attendances, TeachingSessionRepository sessions,
            StudentRepository students, EnrollmentRepository enrollments, SessionQrService qr,
            CourseEmployeeRepository courseEmployees) {
        this.attendances = attendances;
        this.sessions = sessions;
        this.students = students;
        this.enrollments = enrollments;
        this.qr = qr;
        this.courseEmployees = courseEmployees;
    }

    @Transactional(readOnly = true)
    public List<AttendanceListResponse> findBySession(UUID sessionId, UUID employeeId) {
        requireEmployeeAccess(sessionId, employeeId);
        return attendances.findBySessionIdOrderByRecordedAtAscStudentLastNameAscStudentFirstNameAsc(sessionId)
                .stream().map(AttendanceListResponse::from).toList();
    }

    @Transactional
    public void delete(UUID sessionId, UUID attendanceId, UUID employeeId) {
        requireEmployeeAccess(sessionId, employeeId);
        Attendance attendance = attendances.findByIdAndSessionId(attendanceId, sessionId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidencija prisustva nije pronađena."));
        attendances.delete(attendance);
    }

    @Transactional
    public AttendanceResponse record(Jwt jwt, String token, String pin, UUID requestId,
            AttendanceClientDetails client) {
        boolean hasToken = token != null && !token.isBlank();
        boolean hasPin = pin != null && !pin.isBlank();
        if (hasToken == hasPin) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pošaljite QR token ili PIN kod.");
        }
        Student student = students.findByOidcIssuerAndOidcSubject(
                jwt.getClaimAsString("identity_issuer"), jwt.getClaimAsString("identity_subject"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Studentski nalog nije povezan sa evidencijom."));
        if (requestId != null) {
            var saved = attendances.findByRequestIdAndStudentId(requestId, student.getId());
            if (saved.isPresent()) return AttendanceResponse.from(saved.get());
        }
        UUID sessionId = hasToken ? qr.validate(token) : qr.validatePin(pin);
        TeachingSession session = sessions.findForUpdate(sessionId).orElseThrow(() -> invalidQr());
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sesija više nije aktivna.");
        }
        if (requestId != null) {
            var saved = attendances.findByRequestIdAndStudentId(requestId, student.getId());
            if (saved.isPresent()) return AttendanceResponse.from(saved.get());
        }
        Enrollment enrollment = enrollments.findFirstByStudentIdAndOfferingId(
                student.getId(), session.getGroup().getOffering().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Student nije upisan na predmet ove sesije."));
        if (session.getActivityType() != ActivityType.LECTURE
                && enrollment.getGroups().stream().noneMatch(group -> group.getId().equals(session.getGroup().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Student ne pripada grupi izabranoj za ovu sesiju.");
        }
        if (attendances.existsBySessionIdAndStudentId(sessionId, student.getId())) {
            throw duplicate();
        }
        try {
            Attendance attendance = attendances.saveAndFlush(new Attendance(session, student, qr.now(),
                    client.ipAddress(), client.userAgent(), requestId));
            return AttendanceResponse.from(attendance);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    private TeachingSession requireEmployeeAccess(UUID sessionId, UUID employeeId) {
        TeachingSession session = sessions.findById(sessionId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesija nije pronađena."));
        if (!courseEmployees.existsByCourseIdAndEmployeeId(session.getCourse().getId(), employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nemate pristup sesijama ovog predmeta.");
        }
        return session;
    }

    private ResponseStatusException duplicate() {
        return new DuplicateAttendanceException();
    }

    private ResponseStatusException invalidQr() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR token nije važeći.");
    }
}
