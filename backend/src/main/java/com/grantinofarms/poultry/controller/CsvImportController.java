package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.CsvImportRequest;
import com.grantinofarms.poultry.service.CsvImportService;
import com.grantinofarms.poultry.service.FeedHistoryCsvImportService;
import com.grantinofarms.poultry.service.OperationalCsvImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/imports")
public class CsvImportController {
    private final CsvImportService service;
    private final FeedHistoryCsvImportService feedHistoryService;
    private final OperationalCsvImportService operationalService;

    public CsvImportController(CsvImportService service, OperationalCsvImportService operationalService) {
        this.service = service;
        this.operationalService = operationalService;
    }

    @PostMapping("/csv/operational/preview")
    public Map<String, Object> previewOperationalCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", operationalService.preview(request));
    }

    @PostMapping("/csv/operational")
    public Map<String, Object> importOperationalCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", operationalService.importRecords(request));
    }

    @PostMapping("/csv/feed/preview")
    public Map<String, Object> previewFeedCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", feedHistoryService.preview(request));
    }

    @PostMapping("/csv/feed")
    public Map<String, Object> importFeedCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", feedHistoryService.importRecords(request));
    }

    @PostMapping("/csv")
    public Map<String, Object> importCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", service.importRecords(request));
    }
}
