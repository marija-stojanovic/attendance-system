package com.attendance.course.reports;

import com.attendance.course.ActivityType;
import com.attendance.course.CourseEmployeeRole;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

@Service
public class SummaryPdfExportService {
    private final AttendanceSummaryService summaries;

    public SummaryPdfExportService(AttendanceSummaryService summaries) {
        this.summaries = summaries;
    }

    public byte[] export(UUID offeringId, UUID employeeId) {
        ReportData.Summary report = summaries.summary(offeringId, employeeId);
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream();
                var fontStream = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            if (fontStream == null) throw new IOException("Font za PDF nije pronađen.");
            PDType0Font font = PDType0Font.load(document, fontStream);
            PDRectangle landscape = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            PdfReportWriter pages = new PdfReportWriter(document, font, landscape,
                    "Zbirna evidencija prisustva", new float[] {100, 110, 80, 240, 70, 70, 82});
            pages.start();
            var offering = report.offering();
            pages.detail("Predmet", offering.courseName());
            pages.detail("Šifra", offering.courseCode());
            pages.detail("Akademska godina", offering.academicYear());
            pages.detail("Studijski program", offering.studyProgram());
            pages.detail("Tip studija", offering.studyType());
            pages.detail("Godina studija", Short.toString(offering.yearOfStudy()));
            pages.detail("Semestar", Short.toString(offering.semester()));
            pages.detail("Oblik nastave i grupa", report.activityGroups().stream()
                    .map(group -> activityName(group.activityType()) + " - "
                            + (group.activityType() == ActivityType.LECTURE ? "sve grupe" : group.groupNumber()))
                    .collect(Collectors.joining("; ")));
            pages.detail("Nastavnik", employees(offering, CourseEmployeeRole.TEACHER));
            pages.detail("Asistent", employees(offering, CourseEmployeeRole.ASSISTANT));
            pages.detail("Održanih sesija", Integer.toString(report.closedSessions()));
            pages.detail("Upisanih studenata", Integer.toString(report.students().size()));
            pages.headers("Ime", "Prezime", "Broj indeksa", "E-mail", "Dolasci", "Izostanci", "Prisustvo");
            for (var entry : report.students()) {
                var student = entry.student();
                pages.row(student.firstName(), student.lastName(), student.indexNumber(), student.email(),
                        Integer.toString(entry.arrivals()), Integer.toString(entry.absences()),
                        entry.attendancePercent().toPlainString() + "%");
            }
            pages.finish();
            document.save(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("PDF izvoz zbirne evidencije nije uspio.", error);
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
