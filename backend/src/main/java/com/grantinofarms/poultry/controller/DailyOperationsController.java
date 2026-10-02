package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.DailyRecordRequest;
import com.grantinofarms.poultry.dto.DailyRecordResponse;
import com.grantinofarms.poultry.service.DailyOperationsService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/batches/{batchId}/daily-records")
public class DailyOperationsController {
    private final DailyOperationsService service;

    public DailyOperationsController(DailyOperationsService service) {
        this.service = service;
    }

    @PostMapping
    public DailyRecordResponse create(
            @PathVariable String batchId,
            @Valid @RequestBody DailyRecordRequest request
    ) {
        return service.create(batchId, request);
    }

    @GetMapping("/{date}")
    public DailyRecordResponse get(
            @PathVariable String batchId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return service.get(batchId, date);
    }

    @GetMapping
    public List<DailyRecordResponse> list(
            @PathVariable String batchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return service.list(batchId, from, to);
    }
}
