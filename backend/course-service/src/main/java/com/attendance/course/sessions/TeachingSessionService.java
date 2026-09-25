package com.attendance.course.sessions;

import com.attendance.course.CourseRepository;
import com.attendance.course.TeachingGroup;
import com.attendance.course.TeachingGroupRepository;
import com.attendance.course.CourseEmployeeRepository;
import com.attendance.course.realtime.QrRotationNotifier;
import com.attendance.course.realtime.RealtimePublisher;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class TeachingSessionService {
    private final TeachingSessionRepository sessions;
    private final CourseRepository courses;
    private final TeachingGroupRepository groups;
    private final SessionQrService qr;
    private final CourseEmployeeRepository courseEmployees;
    private final RealtimePublisher realtime;
    private final QrRotationNotifier qrRotations;

    public TeachingSessionService(TeachingSessionRepository sessions, CourseRepository courses,
            TeachingGroupRepository groups, SessionQrService qr, CourseEmployeeRepository courseEmployees,
            RealtimePublisher realtime, QrRotationNotifier qrRotations) {
        this.sessions = sessions;
        this.courses = courses;
        this.groups = groups;
        this.qr = qr;
        this.courseEmployees = courseEmployees;
        this.realtime = realtime;
        this.qrRotations = qrRotations;
    }

    @Transactional
    public SessionResponse create(UUID courseId, SessionRequest request, UUID employeeId) {
        requireCourseAccess(courseId, employeeId);
        return create(courseId, request);
    }

    @Transactional
    public SessionResponse create(UUID courseId, SessionRequest request) {
        requireCourse(courseId);
        TeachingGroup group = requireGroup(courseId, request.groupId());
        TeachingSession session = sessions.saveAndFlush(
                new TeachingSession(group, request.date(), request.startTime(), request.qrRotationSeconds()));
        changed(session);
        return SessionResponse.from(session);
    }

    public List<SessionResponse> findByCourse(UUID courseId) {
        requireCourse(courseId);
        return sessions.findByGroupOfferingCourseIdOrderByDateAscStartTimeAscIdAsc(courseId)
                .stream().map(SessionResponse::from).toList();
    }

    public List<SessionResponse> findByCourse(UUID courseId, UUID employeeId) {
        requireCourseAccess(courseId, employeeId);
        return findByCourse(courseId);
    }

    public SessionResponse findById(UUID id) {
        return SessionResponse.from(sessions.findById(id).orElseThrow(() -> notFound()));
    }

    public SessionResponse findById(UUID id, UUID employeeId) {
        requireAccess(id, employeeId);
        return findById(id);
    }

    @Transactional
    public SessionResponse update(UUID id, SessionRequest request) {
        TeachingSession session = requireForUpdate(id);
        requireStatus(session, SessionStatus.CREATED, "Samo neaktivirana sesija može se mijenjati.");
        TeachingGroup group = requireGroup(session.getCourse().getId(), request.groupId());
        session.update(group, request.date(), request.startTime(), request.qrRotationSeconds());
        changed(session);
        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse update(UUID id, SessionRequest request, UUID employeeId) {
        requireAccess(id, employeeId);
        return update(id, request);
    }

    @Transactional
    public SessionResponse activate(UUID id) {
        TeachingSession session = requireForUpdate(id);
        requireStatus(session, SessionStatus.CREATED, "Samo kreirana sesija može se aktivirati.");
        session.activate(qr.now());
        qr.generate(session);
        qrRotations.schedule(session);
        changed(session);
        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse activate(UUID id, UUID employeeId) {
        requireAccess(id, employeeId);
        return activate(id);
    }

    @Transactional
    public SessionResponse close(UUID id) {
        TeachingSession session = requireForUpdate(id);
        requireStatus(session, SessionStatus.ACTIVE, "Samo aktivna sesija može se zatvoriti.");
        session.close();
        qrRotations.cancel(session.getId());
        changed(session);
        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse close(UUID id, UUID employeeId) {
        requireAccess(id, employeeId);
        return close(id);
    }

    public void requireAccess(UUID sessionId, UUID employeeId) {
        if (employeeId == null) return;
        TeachingSession session = sessions.findById(sessionId).orElseThrow(() -> notFound());
        requireCourseAccess(session.getCourse().getId(), employeeId);
    }

    private void requireCourseAccess(UUID courseId, UUID employeeId) {
        if (employeeId != null && !courseEmployees.existsByCourseIdAndEmployeeId(courseId, employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nemate pristup sesijama ovog predmeta.");
        }
    }

    private TeachingSession requireForUpdate(UUID id) {
        return sessions.findForUpdate(id).orElseThrow(() -> notFound());
    }

    private void requireCourse(UUID id) {
        if (!courses.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Predmet nije pronađen.");
        }
    }

    private TeachingGroup requireGroup(UUID courseId, UUID groupId) {
        TeachingGroup group = groups.findById(groupId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupa nije pronađena."));
        if (!group.getOffering().getCourse().getId().equals(courseId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grupa ne pripada izabranom predmetu.");
        }
        return group;
    }

    private void requireStatus(TeachingSession session, SessionStatus expected, String message) {
        if (session.getStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesija nije pronađena.");
    }

    private void changed(TeachingSession session) {
        realtime.sessionChanged(session.getCourse().getId(), session.getGroup().getOffering().getId(), session.getId());
    }
}
