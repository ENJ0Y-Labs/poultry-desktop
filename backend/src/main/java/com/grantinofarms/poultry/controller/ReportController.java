package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.service.ReportService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service){this.service=service;}
    @GetMapping("/farm") public Map<String,Object> farm(@RequestParam(required=false) LocalDate asOf){return Map.of("ok",true,"data",service.farm(asOf));}
    @GetMapping("/batches/{id}") public Map<String,Object> batch(@PathVariable String id,@RequestParam(required=false) LocalDate asOf){return Map.of("ok",true,"data",service.batch(id,asOf));}

    @GetMapping(value="/farm.csv", produces="text/csv")
    public ResponseEntity<String> farmCsv(@RequestParam(required=false) LocalDate asOf) {
        return csvResponse(service.farm(asOf), "farm-report.csv");
    }

    @GetMapping(value="/batches/{id}.csv", produces="text/csv")
    public ResponseEntity<String> batchCsv(@PathVariable String id,@RequestParam(required=false) LocalDate asOf) {
        return csvResponse(service.batch(id,asOf), "batch-report.csv");
    }

    private ResponseEntity<String> csvResponse(Map<String,Object> report, String filename) {
        StringBuilder csv = new StringBuilder("section,key,value\n");
        Map<String,Object> dashboard = (Map<String,Object>) report.get("dashboard");
        dashboard.forEach((key,value) -> csv.append(row("dashboard", key, value)));
        for (Object item : (java.util.List<?>) report.getOrDefault("sales", java.util.List.of())) {
            csv.append(row("sale", "record", item));
        }
        for (Object item : (java.util.List<?>) report.getOrDefault("expenses", java.util.List.of())) {
            csv.append(row("expense", "record", item));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="" + filename + """)
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    private String row(String section, String key, Object value) {
        return escape(section) + "," + escape(key) + "," + escape(String.valueOf(value)) + "\n";
    }

    private String escape(String value) {
        return """ + value.replace(""", """") + """;
    }
}
