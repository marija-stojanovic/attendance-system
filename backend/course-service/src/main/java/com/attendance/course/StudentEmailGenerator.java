package com.attendance.course;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StudentEmailGenerator {
    private final String domain;

    public StudentEmailGenerator(@Value("${students.email-domain:student.etf.unibl.org}") String domain) {
        this.domain = domain.strip().toLowerCase(Locale.ROOT);
        if (this.domain.length() > 189 || !this.domain.matches(
                "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+")) {
            throw new IllegalArgumentException("Domen studentskog emaila nije ispravan.");
        }
    }

    public String generate(String firstName, String lastName, int number) {
        String first = part(firstName);
        String last = part(lastName);
        String suffix = number == 1 ? "" : "." + number;
        int available = 63 - suffix.length();
        int firstLength = Math.min(first.length(), available / 2);
        int lastLength = Math.min(last.length(), available - firstLength);
        firstLength = Math.min(first.length(), available - lastLength);
        first = first.substring(0, firstLength).replaceAll("-+$", "");
        last = last.substring(0, lastLength).replaceAll("-+$", "");
        return first + "." + last + suffix + "@" + domain;
    }

    private String part(String value) {
        String lower = Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        String cyrillic = "абвгдђежзијклљмнњопрстћуфхцчџш";
        String[] latin = {"a", "b", "v", "g", "d", "dj", "e", "z", "z", "i", "j", "k", "l", "lj",
                "m", "n", "nj", "o", "p", "r", "s", "t", "c", "u", "f", "h", "c", "c", "dz", "s"};
        StringBuilder converted = new StringBuilder();
        for (char character : lower.toCharArray()) {
            int index = cyrillic.indexOf(character);
            converted.append(index >= 0 ? latin[index] : String.valueOf(character));
        }
        String result = Normalizer.normalize(converted.toString().replace("đ", "dj"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[\\s\\p{Z}-]+", "-")
                .replace("'", "").replace("’", "");
        if (!result.matches("[a-z]+(?:-[a-z]+)*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ime i prezime moraju biti zapisani latinicom ili srpskom ćirilicom za pravljenje emaila.");
        }
        return result;
    }
}
