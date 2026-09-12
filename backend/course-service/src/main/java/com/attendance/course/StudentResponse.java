package com.attendance.course;

import java.time.Instant;
import java.util.UUID;

public record StudentResponse(UUID id, String indexNumber, String firstName, String lastName,
                              String institutionalEmail, Instant createdAt) {
    public static StudentResponse from(Student student) {
        return new StudentResponse(student.getId(), student.getIndexNumber(), student.getFirstName(),
                student.getLastName(), student.getInstitutionalEmail(), student.getCreatedAt());
    }
}
