package com.attendance.course.reports;

import com.attendance.course.ActivityType;
import com.attendance.course.CourseEmployeeRole;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

@Service
public class SessionPdfExportService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy.");
    private final AttendanceReportDataService reports;

    public SessionPdfExportService(AttendanceReportDataService reports) {
        this.reports = reports;
    }

    public byte[] export(UUID sessionId, UUID employeeId) {
        ReportData.Session report = reports.session(sessionId, employeeId);
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream();
                var fontStream = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            if (fontStream == null) throw new IOException("Font za PDF nije pronađen.");
            PDType0Font font = PDType0Font.load(document, fontStream);
            PdfReportWriter pages = new PdfReportWriter(document, font, PDRectangle.A4,
                    "Evidencija prisustva", new float[] {101, 110, 75, 229});
            pages.start();
            var offering = report.offering();
            pages.detail("Predmet", offering.courseName());
            pages.detail("Šifra", offering.courseCode());
            pages.detail("Akademska godina", offering.academicYear());
            pages.detail("Studijski program", offering.studyProgram());
            pages.detail("Tip studija", offering.studyType());
            pages.detail("Godina studija", Short.toString(offering.yearOfStudy()));
            pages.detail("Semestar", Short.toString(offering.semester()));
            pages.detail("Oblik nastave", activityName(report.activityType()));
            pages.detail("Grupa", report.activityType() == ActivityType.LECTURE
                    ? "Sve grupe" : report.groupNumber());
            pages.detail("Nastavnik", employees(offering, CourseEmployeeRole.TEACHER));
            pages.detail("Asistent", employees(offering, CourseEmployeeRole.ASSISTANT));
            pages.detail("Datum sesije", DATE.format(report.date()));
            pages.detail("Vrijeme", report.startTime().toString());
            pages.detail("Broj prisutnih", Integer.toString(report.presentStudents().size()));
            pages.headers("Ime", "Prezime", "Broj indeksa", "E-mail");
            for (var student : report.presentStudents()) {
                pages.row(student.firstName(), student.lastName(), student.indexNumber(), student.email());
            }
            pages.finish();
            document.save(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("PDF izvoz evidencije nije uspio.", error);
        }
    }

    private String employees(ReportData.Offering offering, CourseEmployeeRole role) {
        return offering.employees().stream().filter(employee -> employee.role() == role)
                .map(ReportData.Employee::displayName).collect(Collectors.joining(", "));
    }

    private String activityName(ActivityType type) {
        return switch (type) {
            case LECTURE -> "Predavanje";
            case AUDITORY_EXERCISE -> "Auditorne vježbe";
            case LABORATORY_EXERCISE -> "Laboratorijske vježbe";
        };
    }

}
