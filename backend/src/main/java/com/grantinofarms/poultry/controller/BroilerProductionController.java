package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.BroilerProductionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/batches/{batchId}/broiler")
public class BroilerProductionController {
    private final BroilerProductionService service;

    public BroilerProductionController(BroilerProductionService service) {
        this.service = service;
    }

    @PostMapping("/weights")
    public WeightRecordResponse addWeight(
            @PathVariable String batchId,
            @Valid @RequestBody WeightRecordRequest request) {
        return service.addWeight(batchId, request);
    }

    @GetMapping("/weights")
    public List<WeightRecordResponse> weights(
            @PathVariable String batchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.weights(batchId, asOf);
    }

    @GetMapping("/growth")
    public BroilerGrowthResponse growth(
            @PathVariable String batchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.growth(batchId, asOf);
    }

    @PostMapping("/sales")
    public BirdSaleResponse sale(
            @PathVariable String batchId,
            @Valid @RequestBody BirdSaleRequest request) {
        return service.addSale(batchId, request);
    }

    @GetMapping("/sales")
    public List<BirdSaleResponse> sales(
            @PathVariable String batchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.sales(batchId, asOf);
    }
}
