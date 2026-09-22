package com.attendance.course.reports;

import com.attendance.course.ActivityType;
import com.attendance.course.CourseEmployeeRole;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class SessionExcelExportService {
    private final AttendanceReportDataService reports;

    public SessionExcelExportService(AttendanceReportDataService reports) {
        this.reports = reports;
    }

    public byte[] export(UUID sessionId, UUID employeeId) {
        ReportData.Session report = reports.session(sessionId, employeeId);
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Присуство");
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            CellStyle bold = workbook.createCellStyle();
            bold.setFont(boldFont);

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("Евиденција присуства");
            title.getCell(0).setCellStyle(bold);

            var offering = report.offering();
            int row = 2;
            row = detail(sheet, row, "Предмет", offering.courseName(), bold);
            row = detail(sheet, row, "Шифра предмета", offering.courseCode(), bold);
            row = detail(sheet, row, "Академска година", offering.academicYear(), bold);
            row = detail(sheet, row, "Студијски програм", offering.studyProgram(), bold);
            row = detail(sheet, row, "Тип студија", offering.studyType(), bold);
            row = detail(sheet, row, "Година студија", Short.toString(offering.yearOfStudy()), bold);
            row = detail(sheet, row, "Семестар", Short.toString(offering.semester()), bold);
            row = detail(sheet, row, "Облик наставе", activityName(report.activityType()), bold);
            row = detail(sheet, row, "Група", report.activityType() == ActivityType.LECTURE
                    ? "Све групе" : report.groupNumber(), bold);
            row = detail(sheet, row, "Наставник", employees(offering, CourseEmployeeRole.TEACHER), bold);
            row = detail(sheet, row, "Асистент", employees(offering, CourseEmployeeRole.ASSISTANT), bold);
            row = detail(sheet, row, "Датум сесије", report.date().toString(), bold);
            row = detail(sheet, row, "Вријеме", report.startTime().toString(), bold);
            row = detail(sheet, row, "Број присутних", Integer.toString(report.presentStudents().size()), bold);

            int headerRow = row + 1;
            Row header = sheet.createRow(headerRow);
            String[] columns = {"Име", "Презиме", "Број индекса", "Е-пошта"};
            for (int column = 0; column < columns.length; column++) {
                header.createCell(column).setCellValue(columns[column]);
                header.getCell(column).setCellStyle(bold);
            }
            int studentRow = headerRow + 1;
            for (var student : report.presentStudents()) {
                Row cells = sheet.createRow(studentRow++);
                cells.createCell(0).setCellValue(student.firstName());
                cells.createCell(1).setCellValue(student.lastName());
                cells.createCell(2).setCellValue(student.indexNumber());
                cells.createCell(3).setCellValue(student.email());
            }
            sheet.setColumnWidth(0, 26 * 256);
            sheet.setColumnWidth(1, 26 * 256);
            sheet.setColumnWidth(2, 42 * 256);
            sheet.setColumnWidth(3, 55 * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("Izvoz evidencije nije uspio.", error);
        }
    }

    private int detail(org.apache.poi.ss.usermodel.Sheet sheet, int number, String label,
            String value, CellStyle bold) {
        Row row = sheet.createRow(number);
        row.createCell(0).setCellValue(label);
        row.getCell(0).setCellStyle(bold);
        row.createCell(1).setCellValue(value);
        return number + 1;
    }

    private String employees(ReportData.Offering offering, CourseEmployeeRole role) {
        return offering.employees().stream().filter(employee -> employee.role() == role)
                .map(ReportData.Employee::displayName).collect(Collectors.joining(", "));
    }

    private String activityName(ActivityType type) {
        return switch (type) {
            case LECTURE -> "Предавање";
            case AUDITORY_EXERCISE -> "Аудиторне вјежбе";
            case LABORATORY_EXERCISE -> "Лабораторијске вјежбе";
        };
    }
}
