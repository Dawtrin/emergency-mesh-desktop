package com.rescue.mesh.controller;

import com.rescue.mesh.model.AuditLogEntity;
import com.rescue.mesh.service.ReportExportService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * REST API xuất báo cáo IAP & Audit Trail (Trang 6).
 *
 * Endpoints (theo FINAL spec §6.1):
 *   GET /api/reports/iap?format=pdf|csv — Xuất báo cáo
 *   GET /api/reports/audit?limit=100    — Lấy audit trail JSON
 */
@RestController
@RequestMapping("/api/reports")
public class ReportApiController {

    private final ReportExportService reportService;

    public ReportApiController(ReportExportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Xuất dữ liệu nạn nhân chuẩn GeoJSON (Phục vụ phần mềm bản đồ GIS / ATAK).
     */
    @GetMapping(value = "/geojson", produces = "application/geo+json; charset=UTF-8")
    public ResponseEntity<String> exportGeoJson() {
        String geojson = reportService.exportGeoJson();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("Rescue_Incidents_" + System.currentTimeMillis() + ".geojson")
                .build());
        return new ResponseEntity<>(geojson, headers, HttpStatus.OK);
    }

    /**
     * Xuất báo cáo IAP — FINAL spec: GET /api/reports/iap?format=pdf|csv
     * format=csv  → download file CSV
     * format=pdf  → download text report (PDF engine quá nặng cho offline, dùng text thay thế)
     * default     → trả text report
     */
    @GetMapping("/iap")
    public ResponseEntity<?> exportIap(@RequestParam(defaultValue = "text") String format) {
        if ("csv".equalsIgnoreCase(format)) {
            String csv = reportService.exportCsv(500);
            byte[] content = csv.getBytes(StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("IAP_Report_" + System.currentTimeMillis() + ".csv")
                    .build());

            return new ResponseEntity<>(content, headers, HttpStatus.OK);
        }

        // Default: text/plain IAP report
        String report = reportService.exportIapReport();
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(report);
    }

    /**
     * Lấy audit trail dạng JSON (cho hiển thị bảng trên frontend Trang 6).
     */
    @GetMapping(value = "/audit", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AuditLogEntity> getAuditTrail(@RequestParam(defaultValue = "100") int limit) {
        return reportService.buildAuditTrail(Math.min(limit, 500));
    }
}
