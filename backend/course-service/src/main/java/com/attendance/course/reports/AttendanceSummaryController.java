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
@RequestMapping("/api/offerings")
public class AttendanceSummaryController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final AttendanceSummaryService summaries;
    private final SummaryPdfExportService pdf;
    private final SummaryExcelExportService excel;

    public AttendanceSummaryController(AttendanceSummaryService summaries, SummaryPdfExportService pdf,
            SummaryExcelExportService excel) {
        this.summaries = summaries;
        this.pdf = pdf;
        this.excel = excel;
    }

    @GetMapping("/{offeringId}/attendance-summary")
    public ReportData.Summary summary(@PathVariable UUID offeringId, @AuthenticationPrincipal Jwt jwt) {
        return summaries.summary(offeringId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/{offeringId}/attendance-summary.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID offeringId, @AuthenticationPrincipal Jwt jwt) {
        byte[] content = pdf.export(offeringId, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("zbirno-prisustvo-" + offeringId + ".pdf").build().toString())
                .body(content);
    }

    @GetMapping("/{offeringId}/attendance-summary.xlsx")
    public ResponseEntity<byte[]> excel(@PathVariable UUID offeringId, @AuthenticationPrincipal Jwt jwt) {
        byte[] content = excel.export(offeringId, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("zbirno-prisustvo-" + offeringId + ".xlsx").build().toString())
                .body(content);
    }
}
