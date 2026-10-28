package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.CsvImportRequest;
import com.grantinofarms.poultry.service.CsvImportService;
import com.grantinofarms.poultry.service.FeedHistoryCsvImportService;
import com.grantinofarms.poultry.service.OperationalCsvImportService;
import com.grantinofarms.poultry.service.ProductionHistoryCsvImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/imports")
public class CsvImportController {
    private final CsvImportService service;
    private final FeedHistoryCsvImportService feedHistoryService;
    private final OperationalCsvImportService operationalService;
    private final ProductionHistoryCsvImportService productionHistoryService;

    public CsvImportController(CsvImportService service, OperationalCsvImportService operationalService,
                               FeedHistoryCsvImportService feedHistoryService,
                               ProductionHistoryCsvImportService productionHistoryService) {
        this.service = service;
        this.operationalService = operationalService;
        this.feedHistoryService = feedHistoryService;
        this.productionHistoryService = productionHistoryService;
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

    @PostMapping("/csv/production/preview")
    public Map<String, Object> previewProductionCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", productionHistoryService.preview(request));
    }

    @PostMapping("/csv/production")
    public Map<String, Object> importProductionCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", productionHistoryService.importRecords(request));
    }

    @PostMapping("/csv")
    public Map<String, Object> importCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", service.importRecords(request));
    }
}
