package com.attendance.course.sessions;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api")
public class TeachingSessionController {
    private final TeachingSessionService sessions;

    public TeachingSessionController(TeachingSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/courses/{courseId}/sessions")
    public ResponseEntity<SessionResponse> create(@PathVariable UUID courseId,
            @Valid @RequestBody SessionRequest request, @AuthenticationPrincipal Jwt jwt) {
        SessionResponse session = sessions.create(courseId, request, employeeId(jwt));
        return ResponseEntity.created(URI.create("/api/sessions/" + session.id())).body(session);
    }

    @GetMapping("/courses/{courseId}/sessions")
    public List<SessionResponse> findByCourse(@PathVariable UUID courseId, @AuthenticationPrincipal Jwt jwt) {
        return sessions.findByCourse(courseId, employeeId(jwt));
    }

    @GetMapping("/sessions/{id}")
    public SessionResponse findById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return sessions.findById(id, employeeId(jwt));
    }

    @PutMapping("/sessions/{id}")
    public SessionResponse update(@PathVariable UUID id, @Valid @RequestBody SessionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return sessions.update(id, request, employeeId(jwt));
    }

    @PostMapping("/sessions/{id}/activate")
    public SessionResponse activate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return sessions.activate(id, employeeId(jwt));
    }

    @PostMapping("/sessions/{id}/close")
    public SessionResponse close(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return sessions.close(id, employeeId(jwt));
    }

    private UUID employeeId(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }
}
