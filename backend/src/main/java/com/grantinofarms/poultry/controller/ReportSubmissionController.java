package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.ReportSubmissionRequest;
import com.grantinofarms.poultry.service.ReportSubmissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/submissions")
public class ReportSubmissionController {
    private final ReportSubmissionService service;

    public ReportSubmissionController(ReportSubmissionService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReportSubmissionRequest request) {
        return Map.of("ok", true, "data", service.createDraft(request, idempotencyKey));
    }

    @PutMapping("/{id}")
    public Map<String, Object> save(@PathVariable String id,
                                    @Valid @RequestBody ReportSubmissionRequest request) {
        return Map.of("ok", true, "data", service.saveDraft(id, request));
    }

    @PostMapping("/{id}/submit")
    public Map<String, Object> submit(@PathVariable String id) {
        return Map.of("ok", true, "data", service.submit(id));
    }

    @GetMapping
    public Map<String, Object> mine() {
        return Map.of("ok", true, "data", service.mine());
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return Map.of("ok", true, "data", service.get(id));
    }
}
