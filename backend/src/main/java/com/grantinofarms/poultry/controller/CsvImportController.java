package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.CsvImportRequest;
import com.grantinofarms.poultry.service.CsvImportService;
import com.grantinofarms.poultry.service.OperationalCsvImportService;
import com.grantinofarms.poultry.service.HealthHistoryCsvImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/imports")
public class CsvImportController {
    private final CsvImportService service;
    private final OperationalCsvImportService operationalService;
    private final HealthHistoryCsvImportService healthHistoryService;

    public CsvImportController(CsvImportService service, OperationalCsvImportService operationalService, HealthHistoryCsvImportService healthHistoryService) {
        this.service = service;
        this.operationalService = operationalService;
        this.healthHistoryService = healthHistoryService;
    }

    @PostMapping("/csv/operational/preview")
    public Map<String, Object> previewOperationalCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", operationalService.preview(request));
    }

    @PostMapping("/csv/operational")
    public Map<String, Object> importOperationalCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", operationalService.importRecords(request));
    }

    @PostMapping("/csv/health/preview")
    public Map<String, Object> previewHealthCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", healthHistoryService.preview(request));
    }

    @PostMapping("/csv/health")
    public Map<String, Object> importHealthCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", healthHistoryService.importRecords(request));
    }

    @PostMapping("/csv")
    public Map<String, Object> importCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", service.importRecords(request));
    }
}
