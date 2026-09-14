package com.attendance.course.imports;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ImportFileReader {
    static final long MAX_BYTES = 5 * 1024 * 1024;
    private final ImportProperties properties;

    public ImportFileReader(ImportProperties properties) {
        this.properties = properties;
    }

    ImportTable read(MultipartFile file, ImportProperties.Layout layout, String delimiter) {
        if (file.isEmpty()) {
            throw badFile("Fajl je prazan.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Fajl može imati najviše 5 MB.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Dozvoljeni su samo .xlsx i .csv fajlovi.");
        }
        try {
            return name.endsWith(".xlsx") ? readExcel(file, layout) : readCsv(file, delimiter);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw badFile("Fajl nije moguće pročitati. Provjerite format, a za CSV koristite UTF-8 kodiranje.");
        }
    }

    private ImportTable readExcel(MultipartFile file, ImportProperties.Layout layout) throws IOException {
        try (var input = file.getInputStream(); var workbook = new XSSFWorkbook(input)) {
            var sheet = layout.sheetName() == null || layout.sheetName().isBlank()
                    ? (workbook.getNumberOfSheets() == 1 ? workbook.getSheetAt(0) : null)
                    : workbook.getSheet(layout.sheetName());
            if (sheet == null) {
                throw badFile("Traženi radni list nije pronađen ili fajl ima više listova bez izabranog naziva u konfiguraciji.");
            }
            if (sheet.getLastRowNum() >= properties.maxRows() || sheet.getNumMergedRegions() > 10000) {
                throw badFile("Excel fajl prelazi dozvoljeni broj redova ili spojenih oblasti.");
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<ImportTable.TableRow> rows = new ArrayList<>();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                var row = sheet.getRow(r);
                var cells = new HashMap<Integer, ImportTable.Value>();
                if (row != null) {
                    if (row.getLastCellNum() > properties.maxColumns()) {
                        throw badFile("Excel fajl prelazi dozvoljeni broj kolona.");
                    }
                    for (var cell : row) {
                        boolean invalid = cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR
                                || cell.getCellType() == CellType.BOOLEAN;
                        String value = invalid ? "" : ImportField.clean(formatter.formatCellValue(cell));
                        cells.put(cell.getColumnIndex(), new ImportTable.Value(value, invalid));
                    }
                }
                rows.add(new ImportTable.TableRow(r + 1, cells));
            }
            List<ImportTable.Merge> merges = new ArrayList<>();
            for (var merged : sheet.getMergedRegions()) {
                if (merged.getLastRow() >= properties.maxRows() || merged.getLastColumn() >= properties.maxColumns()) {
                    throw badFile("Spojene ćelije prelaze dozvoljeni opseg.");
                }
                merges.add(new ImportTable.Merge(merged.getFirstRow(), merged.getLastRow(),
                        merged.getFirstColumn(), merged.getLastColumn()));
            }
            return new ImportTable(rows, merges);
        }
    }

    private ImportTable readCsv(MultipartFile file, String delimiter) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String text;
        try (var reader = new InputStreamReader(file.getInputStream(), decoder); var writer = new java.io.StringWriter()) {
            reader.transferTo(writer);
            text = writer.toString();
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        char separator = delimiter == null ? detectDelimiter(text) : requestedDelimiter(delimiter);
        var format = CSVFormat.RFC4180.builder().setDelimiter(separator).setIgnoreEmptyLines(false).get();
        List<ImportTable.TableRow> rows = new ArrayList<>();
        try (var parser = format.parse(new StringReader(text))) {
            int startLine = 1;
            for (var record : parser) {
                if (rows.size() >= properties.maxRows() || record.size() > properties.maxColumns()) {
                    throw badFile("CSV fajl prelazi dozvoljeni broj redova ili kolona.");
                }
                var cells = new HashMap<Integer, ImportTable.Value>();
                for (int c = 0; c < record.size(); c++) {
                    cells.put(c, new ImportTable.Value(ImportField.clean(record.get(c)), false));
                }
                rows.add(new ImportTable.TableRow(startLine, cells));
                startLine = Math.toIntExact(parser.getCurrentLineNumber()) + 1;
            }
        }
        return new ImportTable(rows, List.of());
    }

    private char requestedDelimiter(String delimiter) {
        if (!List.of(",", ";", "\t").contains(delimiter)) {
            throw badFile("CSV separator mora biti zarez, tačka-zarez ili tabulator.");
        }
        return delimiter.charAt(0);
    }

    private char detectDelimiter(String text) {
        int[] counts = new int[3];
        String separators = ",;\t";
        boolean quoted = false;
        for (int i = 0; i < Math.min(text.length(), 32768); i++) {
            char current = text.charAt(i);
            if (current == '"') {
                quoted = !quoted;
            } else if (!quoted && separators.indexOf(current) >= 0) {
                counts[separators.indexOf(current)]++;
            }
        }
        int best = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        return separators.charAt(best);
    }

    private ResponseStatusException badFile(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
