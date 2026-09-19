package com.attendance.course.imports;

import com.attendance.course.ActivityType;
import com.attendance.course.CreateStudentRequest;
import com.attendance.course.StudentEmailGenerator;
import com.attendance.course.groups.GroupAssignmentService;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ImportParser {
    private final ImportProperties properties;
    private final Validator validator;
    private final StudentEmailGenerator emails;
    private final GroupAssignmentService groupAssignments;

    public ImportParser(ImportProperties properties, Validator validator, StudentEmailGenerator emails,
                        GroupAssignmentService groupAssignments) {
        this.properties = properties;
        this.validator = validator;
        this.emails = emails;
        this.groupAssignments = groupAssignments;
    }

    ImportPreview parse(ImportTable table, ImportProperties.Layout layout) {
        List<ImportPreview.RowError> errors = new ArrayList<>();
        Header metadataHeader = findHeader(table, layout, ImportField.METADATA, layout.metadataHeaderRow(),
                layout.metadataColumns(), errors, "metadata");
        Header studentHeader = findHeader(table, layout, ImportField.STUDENTS, layout.studentHeaderRow(),
                layout.studentColumns(), errors, "students");
        if (!errors.isEmpty()) {
            return invalid(errors);
        }
        int metadataRow = metadataHeader.row() + layout.metadataValueOffset();
        if (metadataRow >= studentHeader.row()) {
            errors.add(new ImportPreview.RowError(null, "metadata", "Podaci predmeta moraju biti prije zaglavlja studenata."));
            return invalid(errors);
        }
        Map<ImportField, String> metadata = values(table, metadataRow, metadataHeader, errors);
        int sourceRow = table.rows().get(metadataRow).number();
        required(metadata, ImportField.COURSE_NAME, 255, sourceRow, errors);
        required(metadata, ImportField.COURSE_CODE, 50, sourceRow, errors);
        required(metadata, ImportField.STUDY_PROGRAM, 4096, sourceRow, errors);
        required(metadata, ImportField.STUDY_TYPE, 100, sourceRow, errors);
        required(metadata, ImportField.GROUP_NUMBER, 20, sourceRow, errors);
        Short year = positive(metadata.get(ImportField.YEAR_OF_STUDY), sourceRow, "yearOfStudy", errors);
        Short semester = positive(metadata.get(ImportField.SEMESTER), sourceRow, "semester", errors);
        ActivityType activity = activity(metadata.get(ImportField.ACTIVITY_TYPE));
        if (activity == null) {
            errors.add(new ImportPreview.RowError(sourceRow, "activityType", "Oblik nastave nije prepoznat."));
        }
        String academicYear = academicYear(table, studentHeader.row(), errors);
        ImportPreview.Metadata result = new ImportPreview.Metadata(academicYear, metadata.get(ImportField.COURSE_NAME),
                metadata.get(ImportField.COURSE_CODE), metadata.get(ImportField.STUDY_PROGRAM),
                metadata.get(ImportField.STUDY_TYPE), year, semester, activity, metadata.get(ImportField.GROUP_NUMBER));
        List<ImportPreview.StudentRow> students = new ArrayList<>();
        List<ImportPreview.RowWarning> warnings = new ArrayList<>();
        Map<String, Integer> indexes = new HashMap<>();
        Map<String, Integer> emailNumbers = new HashMap<>();
        int start = studentHeader.row() + 1;
        for (var merge : table.merges()) {
            if (merge.firstRow() == studentHeader.row()
                    && studentHeader.columns().values().stream().anyMatch(c -> c >= merge.firstColumn() && c <= merge.lastColumn())) {
                start = Math.max(start, merge.lastRow() + 1);
            }
        }
        for (int r = start; r < table.rows().size(); r++) {
            boolean blank = true;
            for (int column : studentHeader.columns().values()) {
                var cell = table.cell(r, column);
                if (!cell.text().isBlank() || cell.invalid()) {
                    blank = false;
                    break;
                }
            }
            if (blank) {
                continue;
            }
            Map<ImportField, String> data = values(table, r, studentHeader, errors);
            int row = table.rows().get(r).number();
            CreateStudentRequest student = new CreateStudentRequest(data.get(ImportField.INDEX_NUMBER),
                    data.get(ImportField.FIRST_NAME), data.get(ImportField.LAST_NAME));
            validator.validate(student).stream().sorted(java.util.Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .forEach(v -> errors.add(new ImportPreview.RowError(row, v.getPropertyPath().toString(), v.getMessage())));
            required(data, ImportField.STATUS, 30, row, errors);
            String email = "";
            if (!student.firstName().isBlank() && !student.lastName().isBlank()) {
                try {
                    String base = emails.generate(student.firstName(), student.lastName(), 1);
                    int number = emailNumbers.merge(base, 1, Integer::sum);
                    email = emails.generate(student.firstName(), student.lastName(), number);
                } catch (ResponseStatusException exception) {
                    errors.add(new ImportPreview.RowError(row, "name", exception.getReason()));
                }
            }
            if (!student.indexNumber().isBlank()) {
                Integer previous = indexes.putIfAbsent(student.indexNumber(), row);
                if (previous != null) {
                    errors.add(new ImportPreview.RowError(row, "indexNumber", "Broj indeksa je već naveden u redu " + previous + "."));
                }
            }
            String auditoryGroup = null;
            String laboratoryGroup = null;
            GroupAssignmentService.Assignment assignment;
            if (year != null && year == 1) {
                var auditory = groupAssignments.assign(year, ActivityType.AUDITORY_EXERCISE,
                        metadata.get(ImportField.GROUP_NUMBER), student.indexNumber());
                var laboratory = groupAssignments.assign(year, ActivityType.LABORATORY_EXERCISE,
                        metadata.get(ImportField.GROUP_NUMBER), student.indexNumber());
                auditoryGroup = auditory.groupNumber();
                laboratoryGroup = laboratory.groupNumber();
                addWarning(warnings, row, auditory.note());
                addWarning(warnings, row, laboratory.note());
                assignment = activity == null ? new GroupAssignmentService.Assignment(null, null) : switch (activity) {
                    case AUDITORY_EXERCISE -> auditory;
                    case LABORATORY_EXERCISE -> laboratory;
                    case LECTURE -> groupAssignments.assign(year, activity,
                            metadata.get(ImportField.GROUP_NUMBER), student.indexNumber());
                };
            } else {
                assignment = groupAssignments.assign(year, activity, metadata.get(ImportField.GROUP_NUMBER),
                        student.indexNumber());
                addWarning(warnings, row, assignment.note());
            }
            students.add(new ImportPreview.StudentRow(row, students.size() + 1, student.lastName(), student.firstName(),
                    student.indexNumber(), data.get(ImportField.STATUS), email, assignment.groupNumber(),
                    auditoryGroup, laboratoryGroup));
        }
        if (students.isEmpty()) {
            errors.add(new ImportPreview.RowError(null, "students", "Fajl ne sadrži studente."));
        }
        return errors.isEmpty() ? new ImportPreview(true, result, List.copyOf(students), List.of(), List.copyOf(warnings))
                : invalid(errors);
    }

    private String academicYear(ImportTable table, int beforeRow, List<ImportPreview.RowError> errors) {
        Pattern range = Pattern.compile("(?<!\\d)(20\\d{2})\\s*[/\\-]\\s*(20\\d{2})(?!\\d)");
        Pattern single = Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)");
        for (int row = 0; row < beforeRow; row++) {
            for (var cell : table.rows().get(row).cells().values()) {
                String text = cell.text();
                Matcher years = range.matcher(text);
                if (years.find() && Integer.parseInt(years.group(2)) == Integer.parseInt(years.group(1)) + 1) {
                    return years.group(1) + "/" + years.group(2);
                }
                String normalized = ImportField.normalize(text);
                if (normalized.contains("godinu") || normalized.contains("godina") || normalized.contains("year")) {
                    Matcher year = single.matcher(text);
                    if (year.find()) {
                        int first = Integer.parseInt(year.group(1));
                        return first + "/" + (first + 1);
                    }
                }
            }
        }
        return null;
    }

    private Header findHeader(ImportTable table, ImportProperties.Layout layout, List<ImportField> fields,
                              Integer fixedRow, Map<String, Integer> configured, List<ImportPreview.RowError> errors, String section) {
        List<Header> found = new ArrayList<>();
        int from = fixedRow == null ? 0 : fixedRow - 1;
        int to = fixedRow == null ? Math.min(table.rows().size(), properties.headerScanRows()) : Math.min(table.rows().size(), fixedRow);
        for (int r = from; r < to; r++) {
            var columns = new EnumMap<ImportField, Integer>(ImportField.class);
            boolean ambiguous = false;
            for (ImportField field : fields) {
                if (fixedRow != null && configured.containsKey(field.key)) {
                    columns.put(field, configured.get(field.key) - 1);
                    continue;
                }
                for (var cell : table.rows().get(r).cells().entrySet()) {
                    if (!cell.getValue().invalid() && field.matches(cell.getValue().text(), layout)) {
                        if (columns.putIfAbsent(field, cell.getKey()) != null) {
                            ambiguous = true;
                        }
                    }
                }
            }
            if (columns.size() == fields.size()) {
                if (ambiguous || columns.values().stream().distinct().count() != columns.size()) {
                    errors.add(new ImportPreview.RowError(table.rows().get(r).number(), section, "Zaglavlje sadrži nejednoznačne kolone."));
                }
                found.add(new Header(r, columns));
            }
        }
        if (found.size() != 1) {
            errors.add(new ImportPreview.RowError(fixedRow, section, found.isEmpty()
                    ? "Potpuno zaglavlje nije pronađeno. Provjerite nazive kolona i izabrani raspored."
                    : "Pronađeno je više zaglavlja. Jedan fajl treba da sadrži jedan predmet i jednu listu studenata."));
            return null;
        }
        return found.getFirst();
    }

    private Map<ImportField, String> values(ImportTable table, int row, Header header, List<ImportPreview.RowError> errors) {
        var result = new EnumMap<ImportField, String>(ImportField.class);
        for (var entry : header.columns().entrySet()) {
            var value = table.cell(row, entry.getValue());
            if (value.invalid()) {
                errors.add(new ImportPreview.RowError(table.rows().get(row).number(), entry.getKey().key,
                        "Polje mora sadržati vrijednost, bez formule, Excel greške ili logičkog izraza."));
            }
            result.put(entry.getKey(), value.text());
        }
        return result;
    }

    private void required(Map<ImportField, String> data, ImportField field, int max, int row, List<ImportPreview.RowError> errors) {
        String value = data.get(field);
        if (value.isBlank() || value.length() > max) {
            errors.add(new ImportPreview.RowError(row, field.key, "Polje je obavezno i može imati najviše " + max + " znakova."));
        }
    }

    private Short positive(String value, int row, String field, List<ImportPreview.RowError> errors) {
        try {
            short number = Short.parseShort(value);
            if (number > 0) {
                return number;
            }
        } catch (NumberFormatException ignored) {
        }
        errors.add(new ImportPreview.RowError(row, field, "Vrijednost mora biti pozitivan cijeli broj do 32767."));
        return null;
    }

    private ActivityType activity(String value) {
        return switch (ImportField.normalize(value)) {
            case "predavanja", "predavanje", "lecture" -> ActivityType.LECTURE;
            case "vjezbe", "vezbe", "auditorne vjezbe", "auditorne vezbe", "auditory exercise" -> ActivityType.AUDITORY_EXERCISE;
            case "laboratorija", "laboratorijske vjezbe", "laboratorijske vezbe", "laboratorijske", "laboratory exercise" -> ActivityType.LABORATORY_EXERCISE;
            default -> null;
        };
    }

    private void addWarning(List<ImportPreview.RowWarning> warnings, int row, String message) {
        if (message != null) {
            warnings.add(new ImportPreview.RowWarning(row, "groupNumber", message));
        }
    }

    private ImportPreview invalid(List<ImportPreview.RowError> errors) {
        return new ImportPreview(false, null, List.of(), List.copyOf(errors), List.of());
    }

    private record Header(int row, Map<ImportField, Integer> columns) {
    }
}
