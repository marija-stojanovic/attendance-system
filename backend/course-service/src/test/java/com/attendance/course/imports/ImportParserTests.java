package com.attendance.course.imports;

import com.attendance.course.ActivityType;
import com.attendance.course.StudentEmailGenerator;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;

class ImportParserTests {
    static final String CSV = "Предмет;Шифра;Студијски програм;Тип студија;Година студија;Семестар;Облик наставе;Група\n"
            + "Тестни предмет;DEMO-101;Тестни програм;ОСНОВНЕ;1;2;вјежбе;1\n\n"
            + "Презиме;Име;Индекс;Статус\nИзмишљић;Јана;TEST-001/2099;РЕДОВАН\n\n"
            + "Примјерић;Ана Марија;TEST-002/2099;ОБНОВА\n";
    private static ValidatorFactory validation;
    private ImportPreviewService service;

    @BeforeAll
    static void openValidation() {
        validation = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidation() {
        validation.close();
    }

    @BeforeEach
    void setUp() {
        service = service(new ImportProperties(5000, 64, 100, Map.of("etf", layout())));
    }

    @Test
    void readsCyrillicCsvAndSkipsBlankRows() {
        ImportPreview result = csv(CSV);
        assertThat(result.valid()).isTrue();
        assertThat(result.metadata().courseCode()).isEqualTo("DEMO-101");
        assertThat(result.metadata().activityType()).isEqualTo(ActivityType.AUDITORY_EXERCISE);
        assertThat(result.students()).hasSize(2);
        assertThat(result.students().getFirst().row()).isEqualTo(5);
        assertThat(result.students().getLast().row()).isEqualTo(7);
        assertThat(result.students().getLast().firstName()).isEqualTo("Ана Марија");
    }

    @ParameterizedTest
    @ValueSource(strings = {",", ";", "\t"})
    void recognizesCsvDelimitersAndBom(String delimiter) {
        assertThat(csv("\uFEFF" + CSV.replace(";", delimiter)).valid()).isTrue();
    }

    @Test
    void readsQuotedSeparatorsEscapedQuotesAndMultilineFields() {
        String input = CSV.replace("Тестни програм", "\"Програм; \"\"А\"\"\nи Б\"");
        ImportPreview result = csv(input);
        assertThat(result.valid()).isTrue();
        assertThat(result.metadata().studyProgram()).isEqualTo("Програм; \"А\" и Б");
        assertThat(result.students().getFirst().row()).isEqualTo(6);
    }

    @Test
    void findsShiftedAndReorderedHeaders() {
        String input = "Наслов\n\n" + CSV.replace("Презиме;Име;Индекс;Статус", "Статус;Индекс;Име;Презиме")
                .replace("Измишљић;Јана;TEST-001/2099;РЕДОВАН", "РЕДОВАН;TEST-001/2099;Јана;Измишљић")
                .replace("Примјерић;Ана Марија;TEST-002/2099;ОБНОВА", "ОБНОВА;TEST-002/2099;Ана Марија;Примјерић");
        ImportPreview result = csv(input);
        assertThat(result.valid()).isTrue();
        assertThat(result.students().getFirst().lastName()).isEqualTo("Измишљић");
        assertThat(result.students().getFirst().row()).isEqualTo(7);
    }

    @Test
    void reportsAllInvalidRowsAndReturnsNoPartialData() {
        ImportPreview result = csv(CSV.replace("Измишљић;Јана", "Измишљић;").replace("TEST-002/2099;ОБНОВА", "TEST-002/2099;"));
        assertThat(result.valid()).isFalse();
        assertThat(result.students()).isEmpty();
        assertThat(result.metadata()).isNull();
        assertThat(result.errors()).anySatisfy(e -> {
            assertThat(e.row()).isEqualTo(5);
            assertThat(e.field()).isEqualTo("firstName");
        }).anySatisfy(e -> {
            assertThat(e.row()).isEqualTo(7);
            assertThat(e.field()).isEqualTo("status");
        });
    }

    @Test
    void rejectsDuplicateIndexesWithinFile() {
        ImportPreview result = csv(CSV.replace("TEST-002/2099", "TEST-001/2099"));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> {
            assertThat(e.row()).isEqualTo(7);
            assertThat(e.field()).isEqualTo("indexNumber");
            assertThat(e.message()).contains("5");
        });
    }

    @Test
    void rejectsMissingAndAmbiguousHeaders() {
        assertThat(csv(CSV.replace("Шифра", "Непознато")).valid()).isFalse();
        assertThat(csv(CSV + "Презиме;Име;Индекс;Статус\n").valid()).isFalse();
        assertThat(csv(CSV.replace("Презиме;Име;Индекс;Статус", "Презиме;Име;Индекс;Статус;Име")).valid()).isFalse();
    }

    @Test
    void rejectsInvalidMetadataAndEmptyStudentList() {
        ImportPreview result = csv(CSV.replace(";1;2;вјежбе;1", ";0;40000;непознато;"));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(ImportPreview.RowError::field)
                .contains("yearOfStudy", "semester", "activityType", "groupNumber");
        assertThat(csv(CSV.substring(0, CSV.indexOf("Измишљић"))).valid()).isFalse();
    }

    @Test
    void supportsConfiguredAliases() {
        var custom = new ImportProperties.Layout(null, null, null, 1, Map.of(), Map.of(),
                Map.of("courseCode", List.of("Course ID"), "indexNumber", List.of("Student ID")));
        service = service(new ImportProperties(5000, 64, 100, Map.of("etf", custom)));
        assertThat(csv(CSV.replace("Шифра", "Course ID").replace("Индекс", "Student ID")).valid()).isTrue();
    }

    @Test
    void supportsConfiguredRowsAndColumns() {
        var custom = new ImportProperties.Layout(null, 1, 4, 1,
                Map.of("courseName", 1, "courseCode", 2, "studyProgram", 3, "studyType", 4,
                        "yearOfStudy", 5, "semester", 6, "activityType", 7, "groupNumber", 8),
                Map.of("lastName", 1, "firstName", 2, "indexNumber", 3, "status", 4), Map.of());
        service = service(new ImportProperties(5000, 64, 100, Map.of("etf", custom)));
        String input = CSV.replace(CSV.lines().findFirst().orElseThrow(), "A;B;C;D;E;F;G;H")
                .replace("Презиме;Име;Индекс;Статус", "W;X;Y;Z");
        assertThat(csv(input).valid()).isTrue();
    }

    @Test
    void rejectsMalformedCsvAndInvalidEncoding() {
        assertThatThrownBy(() -> csv(CSV + "\"unfinished")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.preview(new MockMultipartFile("file", "test.csv", "text/csv",
                new byte[]{(byte) 0xc3, (byte) 0x28}), "etf", null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsEmptyUnsupportedCorruptAndOversizedFiles() {
        assertThatThrownBy(() -> csv("")).isInstanceOf(ResponseStatusException.class);
        for (String name : List.of("test.xls", "test.xlsx")) {
            assertThatThrownBy(() -> service.preview(new MockMultipartFile("file", name, "application/octet-stream",
                    CSV.getBytes(StandardCharsets.UTF_8)), "etf", null)).isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> service.preview(new MockMultipartFile("file", "test.csv", "text/csv",
                new byte[(int) ImportFileReader.MAX_BYTES + 1]), "etf", null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(413));
    }

    @Test
    void rejectsUnknownLayoutAndInvalidDelimiter() {
        var file = new MockMultipartFile("file", "test.csv", "text/csv", CSV.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.preview(file, "unknown", null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.preview(file, "etf", "|")).isInstanceOf(ResponseStatusException.class);
        assertThat(service.preview(file, "etf", ";").valid()).isTrue();
    }

    @Test
    void enforcesRowAndColumnLimits() {
        service = service(new ImportProperties(5, 64, 5, Map.of("etf", layout())));
        assertThatThrownBy(() -> csv(CSV)).isInstanceOf(ResponseStatusException.class);
        service = service(new ImportProperties(5000, 4, 100, Map.of("etf", layout())));
        assertThatThrownBy(() -> csv(CSV)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void readsMergedExcelHeadersAndValuesWithFormattedIndexes() throws Exception {
        try (var workbook = workbook()) {
            var sheet = workbook.getSheetAt(0);
            sheet.shiftRows(4, 6, 1);
            sheet.addMergedRegion(new CellRangeAddress(3, 4, 0, 0));
            sheet.addMergedRegion(new CellRangeAddress(3, 4, 1, 1));
            sheet.addMergedRegion(new CellRangeAddress(3, 4, 2, 2));
            sheet.addMergedRegion(new CellRangeAddress(3, 4, 3, 3));
            var index = sheet.getRow(5).getCell(2);
            index.setCellValue(1);
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("0000"));
            index.setCellStyle(style);
            ImportPreview result = excel(workbook);
            assertThat(result.valid()).isTrue();
            assertThat(result.students()).hasSize(2);
            assertThat(result.students().getFirst().indexNumber()).isEqualTo("0001");
        }
    }

    @Test
    void rejectsFormulaAndExcelErrorInRequiredFields() throws Exception {
        try (var workbook = workbook()) {
            workbook.getSheetAt(0).getRow(4).getCell(2).setCellFormula("1+1");
            ImportPreview result = excel(workbook);
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.row() == 5 && e.field().equals("indexNumber"));
        }
        try (var workbook = workbook()) {
            workbook.getSheetAt(0).getRow(1).getCell(1).setCellErrorValue(org.apache.poi.ss.usermodel.FormulaError.VALUE.getCode());
            assertThat(excel(workbook).valid()).isFalse();
        }
    }

    @Test
    void rejectsWrongWorksheet() throws Exception {
        try (var workbook = workbook()) {
            workbook.setSheetName(0, "Drugi list");
            assertThatThrownBy(() -> excel(workbook)).isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    void parsesAnonymousRepositoryExamples() throws Exception {
        for (String name : List.of("anonymous-course.csv", "anonymous-course.xlsx")) {
            try (var input = getClass().getResourceAsStream("/imports/" + name)) {
                assertThat(input).isNotNull();
                ImportPreview result = service.preview(new MockMultipartFile("file", name, "application/octet-stream", input), "etf", null);
                assertThat(result.valid()).as(name).isTrue();
                assertThat(result.students()).hasSize(2);
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "local.import.samples", matches = "true")
    void parsesLocalOriginalsWithoutCopyingTheirData() throws Exception {
        Map<String, Integer> expected = Map.of("2228", 233, "2235", 183, "2277", 85, "2278", 95, "2296", 60);
        try (var files = Files.list(Path.of(System.getProperty("local.import.directory")))) {
            var originals = files.filter(p -> p.getFileName().toString().startsWith("st_spisak_studenata_na_predmetu_")
                    && p.toString().endsWith(".xlsx")).toList();
            assertThat(originals).hasSize(5);
            for (Path path : originals) {
                try (var input = Files.newInputStream(path)) {
                    ImportPreview result = service.preview(new MockMultipartFile("file", path.getFileName().toString(),
                            "application/octet-stream", input), "etf", null);
                    assertThat(result.valid()).as("Greške redova: %s", result.errors()).isTrue();
                    assertThat(result.students()).hasSize(expected.get(result.metadata().courseCode()));
                }
            }
        }
    }

    private ImportProperties.Layout layout() {
        return new ImportProperties.Layout("st_spisak_studenata_na_predmetu", null, null, 1, Map.of(), Map.of(), Map.of());
    }

    private ImportPreviewService service(ImportProperties properties) {
        return new ImportPreviewService(properties, new ImportFileReader(properties),
                new ImportParser(properties, validation.getValidator(), new StudentEmailGenerator("student.etf.org")));
    }

    private ImportPreview csv(String content) {
        return service.preview(new MockMultipartFile("file", "test.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8)), "etf", null);
    }

    private XSSFWorkbook workbook() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet("st_spisak_studenata_na_predmetu");
        String[] lines = CSV.split("\n", -1);
        for (int r = 0; r < lines.length; r++) {
            var row = sheet.createRow(r);
            String[] values = lines[r].split(";", -1);
            for (int c = 0; c < values.length; c++) {
                row.createCell(c).setCellValue(values[c]);
            }
        }
        return workbook;
    }

    private ImportPreview excel(XSSFWorkbook workbook) throws Exception {
        var output = new ByteArrayOutputStream();
        workbook.write(output);
        return service.preview(new MockMultipartFile("file", "test.xlsx", "application/octet-stream", output.toByteArray()), "etf", null);
    }
}
