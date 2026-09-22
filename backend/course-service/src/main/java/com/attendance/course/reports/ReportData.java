package com.attendance.course.reports;

import com.attendance.course.ActivityType;
import com.attendance.course.CourseEmployeeRole;
import com.attendance.course.sessions.SessionStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class ReportData {
    private ReportData() {}

    public record Employee(String displayName, String email, CourseEmployeeRole role) {}

    public record Offering(UUID id, String courseCode, String courseName, String academicYear,
                           String studyProgram, String studyType, short yearOfStudy, short semester,
                           List<Employee> employees) {
        public Offering { employees = List.copyOf(employees); }
    }

    public record Student(UUID id, String firstName, String lastName, String indexNumber, String email) {}

    public record Session(UUID id, Offering offering, LocalDate date, LocalTime startTime,
                          SessionStatus status, ActivityType activityType, String groupNumber,
                          List<Student> presentStudents) {
        public Session { presentStudents = List.copyOf(presentStudents); }
    }
}
