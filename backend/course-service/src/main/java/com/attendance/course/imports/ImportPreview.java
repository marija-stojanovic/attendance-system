package com.attendance.course.imports;

import com.attendance.course.ActivityType;
import java.util.List;

public record ImportPreview(boolean valid, Metadata metadata, List<StudentRow> students, List<RowError> errors,
                            List<RowWarning> warnings) {
    public record Metadata(String academicYear, String courseName, String courseCode, String studyProgram, String studyType,
                           Short yearOfStudy, Short semester, ActivityType activityType, String groupNumber) {
    }

    public record StudentRow(int row, int ordinalNumber, String lastName, String firstName, String indexNumber,
                             String status, String institutionalEmail, String assignedGroup,
                             String auditoryGroup, String laboratoryGroup) {
    }

    public record RowError(Integer row, String field, String message) {
    }

    public record RowWarning(Integer row, String field, String message) {
    }
}
