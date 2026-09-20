package com.attendance.course.attendance;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student/attendance")
public class AttendanceController {
    private final AttendanceService attendance;

    public AttendanceController(AttendanceService attendance) {
        this.attendance = attendance;
    }

    @PostMapping
    public AttendanceResponse record(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AttendanceRequest request) {
        return attendance.record(jwt, request.token(), request.pin());
    }
}
