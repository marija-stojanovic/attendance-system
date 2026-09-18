package com.attendance.course.sessions;

import com.attendance.course.CourseRepository;
import com.attendance.course.TeachingGroup;
import com.attendance.course.TeachingGroupRepository;
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

    public TeachingSessionService(TeachingSessionRepository sessions, CourseRepository courses,
            TeachingGroupRepository groups, SessionQrService qr) {
        this.sessions = sessions;
        this.courses = courses;
        this.groups = groups;
        this.qr = qr;
    }

    @Transactional
    public SessionResponse create(UUID courseId, SessionRequest request) {
        requireCourse(courseId);
        TeachingGroup group = requireGroup(courseId, request.groupId());
        return SessionResponse.from(sessions.saveAndFlush(
                new TeachingSession(group, request.date(), request.startTime())));
    }

    public List<SessionResponse> findByCourse(UUID courseId) {
        requireCourse(courseId);
        return sessions.findByGroupOfferingCourseIdOrderByDateAscStartTimeAscIdAsc(courseId)
                .stream().map(SessionResponse::from).toList();
    }

    public SessionResponse findById(UUID id) {
        return SessionResponse.from(sessions.findById(id).orElseThrow(() -> notFound()));
    }

    @Transactional
    public SessionResponse update(UUID id, SessionRequest request) {
        TeachingSession session = requireForUpdate(id);
        requireStatus(session, SessionStatus.CREATED, "Samo neaktivirana sesija može se mijenjati.");
        TeachingGroup group = requireGroup(session.getCourse().getId(), request.groupId());
        session.update(group, request.date(), request.startTime());
        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse activate(UUID id) {
        TeachingSession session = requireForUpdate(id);
        requireStatus(session, SessionStatus.CREATED, "Samo kreirana sesija može se aktivirati.");
        session.activate(qr.now());
        qr.generate(session);
        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse close(UUID id) {
        TeachingSession session = requireForUpdate(id);
        requireStatus(session, SessionStatus.ACTIVE, "Samo aktivna sesija može se zatvoriti.");
        session.close();
        return SessionResponse.from(session);
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
}
