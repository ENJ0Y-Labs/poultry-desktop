package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.service.ReportExportService;
import com.grantinofarms.poultry.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService service;
    private final ReportExportService exportService;

    public ReportController(ReportService service, ReportExportService exportService) {
        this.service = service;
        this.exportService = exportService;
    }

    @GetMapping("/farm")
    public Map<String, Object> farm(@RequestParam(required = false) LocalDate asOf) {
        return Map.of("ok", true, "data", service.farm(asOf));
    }

    @GetMapping("/batches/{id}")
    public Map<String, Object> batch(@PathVariable String id, @RequestParam(required = false) LocalDate asOf) {
        return Map.of("ok", true, "data", service.batch(id, asOf));
    }

    @GetMapping(value = "/farm.csv", produces = "text/csv")
    public ResponseEntity<byte[]> farmCsv(@RequestParam(required = false) LocalDate asOf) {
        return csvResponse(service.farm(asOf), "farm-report.csv");
    }

    @GetMapping(value = "/batches/{id}.csv", produces = "text/csv")
    public ResponseEntity<byte[]> batchCsv(
            @PathVariable String id,
            @RequestParam(required = false) LocalDate asOf
    ) {
        return csvResponse(service.batch(id, asOf), "batch-report.csv");
    }

    @GetMapping(value = "/farm.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> farmPdf(@RequestParam(required = false) LocalDate asOf) {
        return pdfResponse(service.farm(asOf), "Farm Report", "farm-report.pdf");
    }

    @GetMapping(value = "/batches/{id}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> batchPdf(
            @PathVariable String id,
            @RequestParam(required = false) LocalDate asOf
    ) {
        return pdfResponse(service.batch(id, asOf), "Batch Report", "batch-report.pdf");
    }

    private ResponseEntity<byte[]> csvResponse(Map<String, Object> report, String filename) {
        byte[] body = exportService.csv(report).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    private ResponseEntity<byte[]> pdfResponse(Map<String, Object> report, String title, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(exportService.pdf(report, title));
    }
}
