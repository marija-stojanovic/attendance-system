package com.attendance.course.reports;

import com.attendance.course.ActivityType;
import com.attendance.course.CourseEmployeeRole;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

@Service
public class SessionPdfExportService {
    private static final float LEFT = 40;
    private static final float[] COLUMN_X = {42, 143, 253, 328};
    private static final float[] COLUMN_WIDTH = {97, 106, 71, 225};
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
            PdfPages pages = new PdfPages(document, font);
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
            pages.tableHeader();
            for (var student : report.presentStudents()) pages.student(student);
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

    private static final class PdfPages {
        private final PDDocument document;
        private final PDType0Font font;
        private PDPageContentStream content;
        private float y;
        private int pageNumber;

        private PdfPages(PDDocument document, PDType0Font font) {
            this.document = document;
            this.font = font;
        }

        private void start() throws IOException {
            newPage();
        }

        private void newPage() throws IOException {
            if (content != null) closePage();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            pageNumber++;
            content = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - 48;
            text(LEFT, y, pageNumber == 1 ? "Evidencija prisustva" : "Evidencija prisustva - nastavak", 16);
            y -= 30;
        }

        private void detail(String label, String value) throws IOException {
            List<String> lines = wrap(value, 390, 9);
            ensureSpace(lines.size() * 14 + 2);
            text(LEFT, y, label + ":", 9);
            for (String line : lines) {
                text(157, y, line, 9);
                y -= 14;
            }
            y -= 2;
        }

        private void tableHeader() throws IOException {
            ensureSpace(45);
            y -= 12;
            content.setNonStrokingColor(232f / 255, 239f / 255, 246f / 255);
            content.addRect(LEFT, y - 8, 515, 22);
            content.fill();
            content.setNonStrokingColor(20f / 255, 35f / 255, 50f / 255);
            String[] labels = {"Ime", "Prezime", "Broj indeksa", "E-mail"};
            for (int column = 0; column < labels.length; column++) text(COLUMN_X[column], y, labels[column], 9);
            y -= 20;
        }

        private void student(ReportData.Student student) throws IOException {
            String[] values = {student.firstName(), student.lastName(), student.indexNumber(), student.email()};
            List<List<String>> cells = new ArrayList<>();
            int maxLines = 1;
            for (int column = 0; column < values.length; column++) {
                List<String> lines = wrap(values[column], COLUMN_WIDTH[column], 8);
                cells.add(lines);
                maxLines = Math.max(maxLines, lines.size());
            }
            int rowHeight = Math.max(19, maxLines * 11 + 7);
            if (y - rowHeight < 55) {
                newPage();
                tableHeader();
            }
            for (int column = 0; column < cells.size(); column++) {
                float lineY = y - 10;
                for (String line : cells.get(column)) {
                    text(COLUMN_X[column], lineY, line, 8);
                    lineY -= 11;
                }
            }
            content.setStrokingColor(218f / 255, 226f / 255, 234f / 255);
            content.moveTo(LEFT, y - rowHeight + 1);
            content.lineTo(555, y - rowHeight + 1);
            content.stroke();
            y -= rowHeight;
        }

        private List<String> wrap(String value, float width, int size) throws IOException {
            if (value == null || value.isBlank()) return List.of("-");
            value = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').strip();
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < value.length();) {
                int codePoint = value.codePointAt(offset);
                String next = new String(Character.toChars(codePoint));
                if (!line.isEmpty() && font.getStringWidth(line + next) * size / 1000 > width) {
                    lines.add(line.toString().stripTrailing());
                    line.setLength(0);
                }
                line.append(next);
                offset += Character.charCount(codePoint);
            }
            if (!line.isEmpty()) lines.add(line.toString().stripTrailing());
            return lines;
        }

        private void ensureSpace(float height) throws IOException {
            if (y - height < 55) newPage();
        }

        private void text(float x, float baseline, String value, int size) throws IOException {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, baseline);
            content.showText(value);
            content.endText();
        }

        private void closePage() throws IOException {
            text(500, 30, "Strana " + pageNumber, 8);
            content.close();
        }

        private void finish() throws IOException {
            closePage();
        }
    }
}
