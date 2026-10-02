package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.BirdPopulationEventRequest;
import com.grantinofarms.poultry.dto.BirdPopulationResponse;
import com.grantinofarms.poultry.dto.BirdTransferRequest;
import com.grantinofarms.poultry.service.BirdPopulationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/batches/{batchId}")
public class BirdPopulationController {
    private final BirdPopulationService populationService;

    public BirdPopulationController(BirdPopulationService populationService) {
        this.populationService = populationService;
    }

    @GetMapping("/population")
    public BirdPopulationResponse population(
            @PathVariable String batchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
    ) {
        return populationService.get(batchId, asOf);
    }

    @PostMapping("/mortality")
    public BirdPopulationResponse mortality(
            @PathVariable String batchId,
            @Valid @RequestBody BirdPopulationEventRequest request
    ) {
        return populationService.addMortality(batchId, request);
    }

    @PostMapping("/culling")
    public BirdPopulationResponse culling(
            @PathVariable String batchId,
            @Valid @RequestBody BirdPopulationEventRequest request
    ) {
        return populationService.addCulling(batchId, request);
    }

    @PostMapping("/bird-sales")
    public BirdPopulationResponse sale(
            @PathVariable String batchId,
            @Valid @RequestBody BirdPopulationEventRequest request
    ) {
        return populationService.addSale(batchId, request);
    }

    @PostMapping("/transfers")
    public BirdPopulationResponse transfer(
            @PathVariable String batchId,
            @Valid @RequestBody BirdTransferRequest request
    ) {
        return populationService.transfer(batchId, request);
    }
}
