package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.dto.HouseUpdateRequest;
import com.grantinofarms.poultry.service.HouseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/farm/houses")
public class HouseController {
    private final HouseService service;

    public HouseController(HouseService service) {
        this.service = service;
    }

    @GetMapping
    ResponseEntity<?> list() {
        return ResponseEntity.ok(Map.of("ok", true, "data", service.list()));
    }

    @PostMapping
    ResponseEntity<?> create(@Valid @RequestBody HouseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("ok", true, "data", service.create(request)));
    }

    @PutMapping("/{id}")
    ResponseEntity<?> update(@PathVariable String id, @Valid @RequestBody HouseUpdateRequest request) {
        return ResponseEntity.ok(Map.of("ok", true, "data", service.update(id, request)));
    }
}
