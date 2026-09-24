package com.attendance.course;

import java.util.UUID;

public record EmployeeReference(UUID id, String email, String username, String displayName) {
    public static EmployeeReference fromToken(UUID id, String email) {
        String normalized = email == null || email.isBlank() ? id + "@unknown.local" : email.strip().toLowerCase();
        String username = normalized.contains("@") ? normalized.substring(0, normalized.indexOf('@')) : normalized;
        return new EmployeeReference(id, normalized, username, normalized);
    }
}
