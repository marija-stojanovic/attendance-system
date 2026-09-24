package com.attendance.course.imports;

import com.attendance.course.EmployeeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

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
    public ResponseEntity<?> commit(@RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String academicYear,
            @RequestParam(defaultValue = "etf") String layout, @RequestParam(required = false) String delimiter,
            @AuthenticationPrincipal Jwt jwt) {
        ImportPreview preview = previewService.preview(file, layout, delimiter);
        if (!preview.valid()) {
            return ResponseEntity.unprocessableEntity().body(preview);
        }
        String year = preview.metadata().academicYear() == null ? academicYear : preview.metadata().academicYear();
        return ResponseEntity.ok(commitService.commit(preview, year, EmployeeReference.fromToken(
                UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"))));
    }
}
