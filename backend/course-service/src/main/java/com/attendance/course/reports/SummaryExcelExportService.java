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
public class SummaryExcelExportService {
    private final AttendanceSummaryService summaries;

    public SummaryExcelExportService(AttendanceSummaryService summaries) {
        this.summaries = summaries;
    }

    public byte[] export(UUID offeringId, UUID employeeId) {
        ReportData.Summary report = summaries.summary(offeringId, employeeId);
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Збирна евиденција");
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            CellStyle bold = workbook.createCellStyle();
            bold.setFont(boldFont);
            CellStyle percentage = workbook.createCellStyle();
            percentage.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("Збирна евиденција присуства");
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
            row = detail(sheet, row, "Облик наставе и група", report.activityGroups().stream()
                    .map(group -> activityName(group.activityType()) + " - "
                            + (group.activityType() == ActivityType.LECTURE ? "све групе" : group.groupNumber()))
                    .collect(Collectors.joining("; ")), bold);
            row = detail(sheet, row, "Наставник", employees(offering, CourseEmployeeRole.TEACHER), bold);
            row = detail(sheet, row, "Асистент", employees(offering, CourseEmployeeRole.ASSISTANT), bold);
            row = detail(sheet, row, "Одржаних сесија", Integer.toString(report.closedSessions()), bold);
            row = detail(sheet, row, "Уписаних студената", Integer.toString(report.students().size()), bold);

            int headerRow = row + 1;
            Row header = sheet.createRow(headerRow);
            String[] columns = {"Име", "Презиме", "Број индекса", "Е-пошта", "Доласци", "Изостанци", "Присуство"};
            for (int column = 0; column < columns.length; column++) {
                header.createCell(column).setCellValue(columns[column]);
                header.getCell(column).setCellStyle(bold);
            }
            int studentRow = headerRow + 1;
            for (var entry : report.students()) {
                var student = entry.student();
                Row cells = sheet.createRow(studentRow++);
                cells.createCell(0).setCellValue(student.firstName());
                cells.createCell(1).setCellValue(student.lastName());
                cells.createCell(2).setCellValue(student.indexNumber());
                cells.createCell(3).setCellValue(student.email());
                cells.createCell(4).setCellValue(entry.arrivals());
                cells.createCell(5).setCellValue(entry.absences());
                cells.createCell(6).setCellValue(entry.attendancePercent().doubleValue() / 100);
                cells.getCell(6).setCellStyle(percentage);
            }
            int[] widths = {24, 26, 20, 44, 14, 14, 16};
            for (int column = 0; column < widths.length; column++) sheet.setColumnWidth(column, widths[column] * 256);
            sheet.createFreezePane(0, headerRow + 1);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("Excel извоз збирне евиденције није успио.", error);
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
