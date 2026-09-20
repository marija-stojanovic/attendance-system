package com.attendance.course.attendance;

import com.attendance.course.*;
import com.attendance.course.sessions.SessionQrService;
import com.attendance.course.sessions.SessionStatus;
import com.attendance.course.sessions.TeachingSession;
import com.attendance.course.sessions.TeachingSessionRepository;
import java.util.UUID;
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

    public AttendanceService(AttendanceRepository attendances, TeachingSessionRepository sessions,
            StudentRepository students, EnrollmentRepository enrollments, SessionQrService qr) {
        this.attendances = attendances;
        this.sessions = sessions;
        this.students = students;
        this.enrollments = enrollments;
        this.qr = qr;
    }

    @Transactional
    public AttendanceResponse record(Jwt jwt, String token) {
        UUID sessionId = qr.validate(token);
        TeachingSession session = sessions.findForUpdate(sessionId).orElseThrow(() -> invalidQr());
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sesija više nije aktivna.");
        }
        Student student = students.findByOidcIssuerAndOidcSubject(
                jwt.getClaimAsString("identity_issuer"), jwt.getClaimAsString("identity_subject"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Studentski nalog nije povezan sa evidencijom."));
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
            Attendance attendance = attendances.saveAndFlush(new Attendance(session, student, qr.now()));
            return AttendanceResponse.from(attendance);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    private ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Prisustvo studenta je već evidentirano za ovu sesiju.");
    }

    private ResponseStatusException invalidQr() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR token nije važeći.");
    }
}
