package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.FarmCreateRequest;
import com.grantinofarms.poultry.dto.FarmResponse;
import com.grantinofarms.poultry.dto.FarmSettingsRequest;
import com.grantinofarms.poultry.dto.FarmSettingsResponse;
import com.grantinofarms.poultry.dto.FarmUpdateRequest;
import com.grantinofarms.poultry.service.FarmService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/farm")
public class FarmController {
    private final FarmService service;

    public FarmController(FarmService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<?> create(@Valid @RequestBody FarmCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true, "data", service.create(request)));
    }

    @GetMapping
    ResponseEntity<?> get() {
        return ResponseEntity.ok(Map.of("ok", true, "data", service.get()));
    }

    @PutMapping
    ResponseEntity<?> update(@Valid @RequestBody FarmUpdateRequest request) {
        return ResponseEntity.ok(Map.of("ok", true, "data", service.update(request)));
    }

    @GetMapping("/settings")
    ResponseEntity<?> getSettings() {
        return ResponseEntity.ok(Map.of("ok", true, "data", service.getSettings()));
    }

    @PutMapping("/settings")
    ResponseEntity<?> updateSettings(@Valid @RequestBody FarmSettingsRequest request) {
        return ResponseEntity.ok(Map.of("ok", true, "data", service.updateSettings(request)));
    }
}
