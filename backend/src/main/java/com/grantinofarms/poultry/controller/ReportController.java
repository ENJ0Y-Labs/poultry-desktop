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
import java.util.List;

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
    public Map<String, Object> farm(
            @RequestParam(required = false) LocalDate asOf,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return Map.of("ok", true, "data", from != null || to != null ? service.farm(from, to) : service.farm(asOf));
    }

    @GetMapping("/batches/compare")
    public Map<String, Object> compareBatches(
            @RequestParam List<String> batchIds,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        return Map.of("ok", true, "data", service.compare(batchIds, from, to));
    }

    @GetMapping("/batches/{id}")
    public Map<String, Object> batch(
            @PathVariable String id,
            @RequestParam(required = false) LocalDate asOf,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return Map.of("ok", true, "data", from != null || to != null ? service.batch(id, from, to) : service.batch(id, asOf));
    }

    @GetMapping(value = "/farm.csv", produces = "text/csv")
    public ResponseEntity<byte[]> farmCsv(
            @RequestParam(required = false) LocalDate asOf,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long periodProfitMinor
    ) {
        Map<String, Object> report = from != null || to != null ? service.farm(from, to) : service.farm(asOf);
        return csvResponse(withPeriodProfit(report, periodProfitMinor), "farm-report.csv");
    }

    @GetMapping(value = "/batches/{id}.csv", produces = "text/csv")
    public ResponseEntity<byte[]> batchCsv(
            @PathVariable String id,
            @RequestParam(required = false) LocalDate asOf,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long periodProfitMinor
    ) {
        Map<String, Object> report = from != null || to != null ? service.batch(id, from, to) : service.batch(id, asOf);
        return csvResponse(withPeriodProfit(report, periodProfitMinor), "batch-report.csv");
    }

    @GetMapping(value = "/farm.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> farmPdf(
            @RequestParam(required = false) LocalDate asOf,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long periodProfitMinor
    ) {
        Map<String, Object> report = from != null || to != null ? service.farm(from, to) : service.farm(asOf);
        return pdfResponse(withPeriodProfit(report, periodProfitMinor), "Farm Report", "farm-report.pdf");
    }

    @GetMapping(value = "/batches/{id}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> batchPdf(
            @PathVariable String id,
            @RequestParam(required = false) LocalDate asOf,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long periodProfitMinor
    ) {
        Map<String, Object> report = from != null || to != null ? service.batch(id, from, to) : service.batch(id, asOf);
        return pdfResponse(withPeriodProfit(report, periodProfitMinor), "Batch Report", "batch-report.pdf");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> withPeriodProfit(Map<String, Object> report, Long periodProfitMinor) {
        if (periodProfitMinor == null) return report;
        Object period = report.get("period");
        if (period instanceof Map<?, ?> values) {
            ((Map<String, Object>) values).put("profitMinor", periodProfitMinor);
        }
        return report;
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
