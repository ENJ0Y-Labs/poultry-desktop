package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.BatchCreateRequest;
import com.grantinofarms.poultry.dto.BatchReopenRequest;
import com.grantinofarms.poultry.service.BatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {
    private final BatchService service;

    public BatchController(BatchService service) {
        this.service = service;
    }

    @GetMapping
    ResponseEntity<?> list() {
        return ResponseEntity.ok(Map.of("ok", true, "data", service.list()));
    }

    @GetMapping("/{id}")
    ResponseEntity<?> get(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("ok", true, "data", service.get(id)));
    }

    @PostMapping
    ResponseEntity<?> create(@Valid @RequestBody BatchCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("ok", true, "data", service.create(request)));
    }

    @PostMapping("/{id}/sold")
    ResponseEntity<?> markSold(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("ok", true, "data", service.markSold(id)));
    }

    @PostMapping("/{id}/reopen")
    ResponseEntity<?> reopen(@PathVariable String id, @Valid @RequestBody BatchReopenRequest request) {
        return ResponseEntity.ok(Map.of("ok", true, "data", service.reopen(id, request)));
    }
}
