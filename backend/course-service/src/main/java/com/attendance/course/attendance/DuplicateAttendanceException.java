package com.attendance.course.attendance;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class DuplicateAttendanceException extends ResponseStatusException {
    public DuplicateAttendanceException() {
        super(HttpStatus.CONFLICT, "Prisustvo studenta je već evidentirano za ovu sesiju.");
    }
}
