package com.attendance.course.sessions;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class SessionQrController {
    private final SessionQrService qr;

    public SessionQrController(SessionQrService qr) {
        this.qr = qr;
    }

    @GetMapping("/{id}/qr")
    public ResponseEntity<SessionQrService.QrResponse> current(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(qr.current(id));
    }
}
