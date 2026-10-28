package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.CsvImportRequest;
import com.grantinofarms.poultry.service.CsvImportService;
import com.grantinofarms.poultry.service.OperationalCsvImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/imports")
public class CsvImportController {
    private final CsvImportService service;
    private final OperationalCsvImportService operationalService;

    public CsvImportController(CsvImportService service) {
        this.service = service;
    }

    @PostMapping("/csv/operational/preview")\n    public Map<String, Object> previewOperationalCsv(@Valid @RequestBody CsvImportRequest request) {\n        return Map.of("ok", true, "data", operationalService.preview(request));\n    }\n\n    @PostMapping("/csv/operational")\n    public Map<String, Object> importOperationalCsv(@Valid @RequestBody CsvImportRequest request) {\n        return Map.of("ok", true, "data", operationalService.importRecords(request));\n    }\n\n    @PostMapping("/csv")
    public Map<String, Object> importCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", service.importRecords(request));
    }
}
