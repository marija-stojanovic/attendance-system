package com.attendance.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStudentRequest(
        @NotBlank(message = "Broj indeksa je obavezan.")
        @Size(max = 30, message = "Broj indeksa može imati najviše 30 znakova.")
        @Pattern(regexp = "[^\\s\\p{Z}]+", message = "Broj indeksa ne smije sadržati razmake.")
        String indexNumber,
        @NotBlank(message = "Ime je obavezno.")
        @Size(max = 100, message = "Ime može imati najviše 100 znakova.")
        @Pattern(regexp = "[\\p{L}\\p{M}]+(?:[ '\u2019-][\\p{L}\\p{M}]+)*", message = "Ime smije sadržati slova, razmake, crtice i apostrofe.")
        String firstName,
        @NotBlank(message = "Prezime je obavezno.")
        @Size(max = 100, message = "Prezime može imati najviše 100 znakova.")
        @Pattern(regexp = "[\\p{L}\\p{M}]+(?:[ '\u2019-][\\p{L}\\p{M}]+)*", message = "Prezime smije sadržati slova, razmake, crtice i apostrofe.")
        String lastName) {

    public CreateStudentRequest {
        indexNumber = normalize(indexNumber);
        firstName = normalize(firstName);
        lastName = normalize(lastName);
    }

    private static String normalize(String value) {
        return value == null ? null : value.replaceAll("[\\s\\p{Z}]+", " ").strip();
    }
}
