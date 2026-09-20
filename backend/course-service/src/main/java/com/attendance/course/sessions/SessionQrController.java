package com.attendance.course.sessions;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class SessionQrController {
    private final SessionQrService qr;
    private final TeachingSessionService sessions;

    public SessionQrController(SessionQrService qr, TeachingSessionService sessions) {
        this.qr = qr;
        this.sessions = sessions;
    }

    @GetMapping("/{id}/qr")
    public ResponseEntity<SessionQrService.QrResponse> current(@PathVariable UUID id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.oauth2.jwt.Jwt jwt) {
        sessions.requireAccess(id, jwt == null ? null : UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(qr.current(id));
    }
}
