package com.attendance.course.reports;

import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionExportController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final SessionExcelExportService excel;
    private final SessionPdfExportService pdf;

    public SessionExportController(SessionExcelExportService excel, SessionPdfExportService pdf) {
        this.excel = excel;
        this.pdf = pdf;
    }

    @GetMapping("/{sessionId}/report.xlsx")
    public ResponseEntity<byte[]> excel(@PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt) {
        byte[] content = excel.export(sessionId, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("prisustvo-sesija-" + sessionId + ".xlsx").build().toString())
                .body(content);
    }

    @GetMapping("/{sessionId}/report.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt) {
        byte[] content = pdf.export(sessionId, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("prisustvo-sesija-" + sessionId + ".pdf").build().toString())
                .body(content);
    }
}
