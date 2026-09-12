package com.attendance.course;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class StudentEmailGeneratorTests {
    private final StudentEmailGenerator generator = new StudentEmailGenerator("student.etf.org");

    @ParameterizedTest
    @CsvSource({
            "Đorđe,Čavić,djordje.cavic",
            "Šime,Žužić,sime.zuzic",
            "Ana Marija,Petrović Jovanović,ana-marija.petrovic-jovanovic",
            "Ana-Marija,O'Neil,ana-marija.oneil",
            "Ђорђе,Чавић,djordje.cavic",
            "Љиљана,Њежић,ljiljana.njezic",
            "Džana,Džonson,dzana.dzonson"
    })
    void transliteratesNames(String first, String last, String local) {
        assertThat(generator.generate(first, last, 1)).isEqualTo(local + "@student.etf.org");
    }

    @Test
    void usesConfiguredDomainAndSuffix() {
        assertThat(new StudentEmailGenerator("students.example.org").generate("Ana", "Anic", 3))
                .isEqualTo("ana.anic.3@students.example.org");
    }

    @Test
    void limitsLocalPartAndPreservesCollisionSuffix() {
        String email = generator.generate("A".repeat(100), "B".repeat(100), 12);
        assertThat(email.split("@")[0]).hasSize(64).endsWith(".12");
        assertThat(email).doesNotContain("..");
    }

    @Test
    void handlesDecomposedDiacritics() {
        assertThat(generator.generate("S\u030cime", "C\u030cavic\u0301", 1)).isEqualTo("sime.cavic@student.etf.org");
    }

    @Test
    void rejectsInvalidDomain() {
        assertThatThrownBy(() -> new StudentEmailGenerator("bad@domain")).isInstanceOf(IllegalArgumentException.class);
    }
}
