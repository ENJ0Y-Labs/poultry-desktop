package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.CsvImportRequest;
import com.grantinofarms.poultry.service.CsvImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/imports")
public class CsvImportController {
    private final CsvImportService service;

    public CsvImportController(CsvImportService service) {
        this.service = service;
    }

    @PostMapping("/csv")
    public Map<String, Object> importCsv(@Valid @RequestBody CsvImportRequest request) {
        return Map.of("ok", true, "data", service.importRecords(request));
    }
}
