package com.attendance.course.imports;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImportPreviewService {
    private final ImportProperties properties;
    private final ImportFileReader reader;
    private final ImportParser parser;

    public ImportPreviewService(ImportProperties properties, ImportFileReader reader, ImportParser parser) {
        this.properties = properties;
        this.reader = reader;
        this.parser = parser;
    }

    public ImportPreview preview(MultipartFile file, String layoutName, String delimiter) {
        ImportProperties.Layout layout = properties.layouts().get(layoutName);
        if (layout == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Izabrani raspored uvoza ne postoji.");
        }
        return parser.parse(reader.read(file, layout, delimiter), layout);
    }
}
