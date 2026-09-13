package com.attendance.course.imports;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
@EnableConfigurationProperties(ImportProperties.class)
public class ImportPreviewController {
    private final ImportPreviewService service;

    public ImportPreviewController(ImportPreviewService service) {
        this.service = service;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportPreview> preview(@RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "etf") String layout, @RequestParam(required = false) String delimiter) {
        ImportPreview preview = service.preview(file, layout, delimiter);
        return ResponseEntity.status(preview.valid() ? 200 : 422).body(preview);
    }
}
