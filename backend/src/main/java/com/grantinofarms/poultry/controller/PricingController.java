package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.PricingResponse;
import com.grantinofarms.poultry.dto.PricingSettingsRequest;
import com.grantinofarms.poultry.dto.PricingSettingsResponse;
import com.grantinofarms.poultry.service.PricingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/pricing")
public class PricingController {
    private final PricingService service;

    public PricingController(PricingService service) {
        this.service = service;
    }

    @GetMapping("/settings")
    public PricingSettingsResponse settings() {
        return service.settings();
    }

    @PutMapping("/settings")
    public PricingSettingsResponse updateSettings(@Valid @RequestBody PricingSettingsRequest request) {
        return service.updateSettings(request);
    }

    @GetMapping("/batches/{batchId}")
    public PricingResponse price(
            @PathVariable String batchId,
            @RequestParam int quantity,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate asOf) {
        return service.price(batchId, quantity, asOf);
    }
}
