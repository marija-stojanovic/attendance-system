package com.attendance.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourseRequest(
        @NotBlank(message = "Šifra predmeta je obavezna.")
        @Size(max = 50, message = "Šifra predmeta može imati najviše 50 znakova.")
        String code,
        @NotBlank(message = "Naziv predmeta je obavezan.")
        @Size(max = 255, message = "Naziv predmeta može imati najviše 255 znakova.")
        String name) {

    public CreateCourseRequest {
        code = code == null ? null : code.strip();
        name = name == null ? null : name.strip();
    }
}
