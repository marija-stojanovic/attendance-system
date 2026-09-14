package com.attendance.course.imports;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
public class ImportCommitController {
    private final ImportPreviewService previewService;
    private final ImportCommitService commitService;

    public ImportCommitController(ImportPreviewService previewService, ImportCommitService commitService) {
        this.previewService = previewService;
        this.commitService = commitService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> commit(@RequestParam("file") MultipartFile file, @RequestParam String academicYear,
            @RequestParam(defaultValue = "etf") String layout, @RequestParam(required = false) String delimiter) {
        ImportPreview preview = previewService.preview(file, layout, delimiter);
        if (!preview.valid()) {
            return ResponseEntity.unprocessableEntity().body(preview);
        }
        return ResponseEntity.ok(commitService.commit(preview, academicYear));
    }
}
