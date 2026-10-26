package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.service.ProductionTrendService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/batches/{batchId}/production-trends")
public class ProductionTrendController {
    private final ProductionTrendService service;

    public ProductionTrendController(ProductionTrendService service) {
        this.service = service;
    }

    @GetMapping("/layer-hdp")
    public Map<String, Object> layerHdp(
            @PathVariable String batchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Map.of("ok", true, "data", service.layerHdp(batchId, from, to));
    }

    @GetMapping("/broiler-average-weight")
    public Map<String, Object> broilerAverageWeight(
            @PathVariable String batchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Map.of("ok", true, "data", service.broilerAverageWeight(batchId, from, to));
    }
}
