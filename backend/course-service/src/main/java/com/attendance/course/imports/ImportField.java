package com.attendance.course.imports;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

enum ImportField {
    COURSE_NAME("courseName", "predmet", "naziv predmeta", "course name"),
    COURSE_CODE("courseCode", "sifra", "sifra predmeta", "course code"),
    STUDY_PROGRAM("studyProgram", "studijski program", "study program"),
    STUDY_TYPE("studyType", "tip studija", "vrsta studija", "study type"),
    YEAR_OF_STUDY("yearOfStudy", "godina studija", "year of study"),
    SEMESTER("semester", "semestar", "semester"),
    ACTIVITY_TYPE("activityType", "oblik nastave", "oblik", "activity type"),
    GROUP_NUMBER("groupNumber", "grupa", "broj grupe", "group number"),
    LAST_NAME("lastName", "prezime", "last name"),
    FIRST_NAME("firstName", "ime", "first name"),
    INDEX_NUMBER("indexNumber", "indeks", "broj indeksa", "index number"),
    STATUS("status", "status", "status studenta", "student status");

    static final List<ImportField> METADATA = Arrays.asList(values()).subList(0, 8);
    static final List<ImportField> STUDENTS = Arrays.asList(values()).subList(8, 12);
    final String key;
    final List<String> aliases;

    ImportField(String key, String... aliases) {
        this.key = key;
        this.aliases = List.of(aliases);
    }

    boolean matches(String text, ImportProperties.Layout layout) {
        String normalized = normalize(text);
        return layout.aliases().getOrDefault(key, aliases).stream()
                .anyMatch(alias -> normalize(alias).equals(normalized));
    }

    static String clean(String text) {
        return text == null ? "" : text.replaceAll("[\\s\\p{Z}]+", " ").strip();
    }

    static String normalize(String text) {
        String source = clean(text).toLowerCase(Locale.ROOT).replace("đ", "dj");
        String letters = "абвгдђежзијклљмнњопрстћуфхцчџш";
        String[] latin = {"a", "b", "v", "g", "d", "dj", "e", "z", "z", "i", "j", "k", "l", "lj",
                "m", "n", "nj", "o", "p", "r", "s", "t", "c", "u", "f", "h", "c", "c", "dz", "s"};
        StringBuilder result = new StringBuilder();
        for (char letter : source.toCharArray()) {
            int index = letters.indexOf(letter);
            result.append(index < 0 ? String.valueOf(letter) : latin[index]);
        }
        return Normalizer.normalize(result, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", " ").strip();
    }
}
