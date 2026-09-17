package com.attendance.course.sessions;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TeachingSessionController {
    private final TeachingSessionService sessions;

    public TeachingSessionController(TeachingSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/courses/{courseId}/sessions")
    public ResponseEntity<SessionResponse> create(@PathVariable UUID courseId,
            @Valid @RequestBody SessionRequest request) {
        SessionResponse session = sessions.create(courseId, request);
        return ResponseEntity.created(URI.create("/api/sessions/" + session.id())).body(session);
    }

    @GetMapping("/courses/{courseId}/sessions")
    public List<SessionResponse> findByCourse(@PathVariable UUID courseId) {
        return sessions.findByCourse(courseId);
    }

    @GetMapping("/sessions/{id}")
    public SessionResponse findById(@PathVariable UUID id) {
        return sessions.findById(id);
    }

    @PutMapping("/sessions/{id}")
    public SessionResponse update(@PathVariable UUID id, @Valid @RequestBody SessionRequest request) {
        return sessions.update(id, request);
    }

    @PostMapping("/sessions/{id}/activate")
    public SessionResponse activate(@PathVariable UUID id) {
        return sessions.activate(id);
    }

    @PostMapping("/sessions/{id}/close")
    public SessionResponse close(@PathVariable UUID id) {
        return sessions.close(id);
    }
}
