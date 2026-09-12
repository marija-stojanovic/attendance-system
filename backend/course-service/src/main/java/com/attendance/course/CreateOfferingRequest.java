package com.attendance.course;

import jakarta.validation.constraints.*;

public record CreateOfferingRequest(
        @NotBlank @Pattern(regexp = "[0-9]{4}/[0-9]{4}", message = "Akademska godina mora biti u obliku 2026/2027.") String academicYear,
        @NotBlank String studyProgram,
        @NotBlank @Size(max = 100) String studyType,
        @NotNull @Positive Short yearOfStudy,
        @NotNull @Positive Short semester) {
    public CreateOfferingRequest {
        academicYear = academicYear == null ? null : academicYear.strip();
        studyProgram = studyProgram == null ? null : studyProgram.strip();
        studyType = studyType == null ? null : studyType.strip();
    }
}
