package com.attendance.course.attendance;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class EmployeeAttendanceController {
    private final AttendanceService attendance;

    public EmployeeAttendanceController(AttendanceService attendance) {
        this.attendance = attendance;
    }

    @GetMapping("/{sessionId}/attendances")
    public List<AttendanceListResponse> findBySession(@PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        return attendance.findBySession(sessionId, UUID.fromString(jwt.getSubject()));
    }
}
