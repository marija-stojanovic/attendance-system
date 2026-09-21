package com.attendance.course.attendance;

import jakarta.servlet.http.HttpServletRequest;

public record AttendanceClientDetails(String ipAddress, String userAgent) {
    public static AttendanceClientDetails from(HttpServletRequest request) {
        return new AttendanceClientDetails(normalize(request.getRemoteAddr(), 45),
                normalize(request.getHeader("User-Agent"), 512));
    }

    private static String normalize(String value, int maximumLength) {
        if (value == null || value.isBlank()) return "unknown";
        String normalized = value.replaceAll("[\\p{Cntrl}&&[^\\t]]", "").strip();
        if (normalized.isBlank()) return "unknown";
        return normalized.length() <= maximumLength ? normalized : normalized.substring(0, maximumLength);
    }
}
