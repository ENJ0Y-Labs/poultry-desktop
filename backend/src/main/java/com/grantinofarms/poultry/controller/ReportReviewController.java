package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.service.ReportReviewService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/review")
public class ReportReviewController {
    private final ReportReviewService service;
    private final com.grantinofarms.poultry.service.ReportMergeService merges;
    private final com.grantinofarms.poultry.service.FarmIncidentService incidents;
    private final com.grantinofarms.poultry.service.ReportCorrectionService corrections;

    public ReportReviewController(ReportReviewService service,
                                  com.grantinofarms.poultry.service.ReportMergeService merges,
                                  com.grantinofarms.poultry.service.FarmIncidentService incidents,
                                  com.grantinofarms.poultry.service.ReportCorrectionService corrections) {
        this.service = service;
        this.merges = merges;
        this.incidents = incidents;
        this.corrections = corrections;
    }

    @GetMapping("/notifications")
    public Map<String, Object> notifications() {
        return Map.of("ok", true, "data", incidents.notifications());
    }

    @PatchMapping("/notifications/{id}/read")
    public Map<String, Object> markNotificationRead(@PathVariable String id) {
        incidents.markNotificationRead(id);
        return Map.of("ok", true, "data", Map.of("read", true));
    }

    @GetMapping("/queue")
    public Map<String, Object> queue(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String authorId) {
        return Map.of("ok", true, "data", service.queue(status, reportType, authorId));
    }

    @GetMapping("/managers")
    public Map<String, Object> managers() {
        return Map.of("ok", true, "data", service.managers());
    }

    @GetMapping("/submissions/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return Map.of("ok", true, "data", service.get(id));
    }

    @PostMapping("/submissions/{id}/assign")
    public Map<String, Object> assign(@PathVariable String id, @RequestBody Map<String, String> body) {
        return Map.of("ok", true, "data", service.assign(id, body == null ? null : body.get("managerId")));
    }

    @PostMapping("/submissions/{id}/comments")
    public Map<String, Object> comment(@PathVariable String id, @RequestBody CommentRequest request) {
        return Map.of("ok", true, "data", service.comment(id, request == null ? null : request.body()));
    }

    @PostMapping("/submissions/{id}/checks/{checkId}/override")
    public Map<String, Object> overrideWarning(@PathVariable String id, @PathVariable String checkId,
                                               @RequestBody OverrideRequest request) {
        return Map.of("ok", true, "data", service.overrideWarning(id, checkId,
                request == null ? null : request.reason()));
    }

    @PostMapping("/submissions/{id}/merge")
    public Map<String, Object> merge(@PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String operationKey) {
        return Map.of("ok", true, "data", merges.merge(id, operationKey));
    }

    @PostMapping("/submissions/{id}/corrections/weight")
    public Map<String, Object> correctWeight(@PathVariable String id,
            @jakarta.validation.Valid @RequestBody com.grantinofarms.poultry.dto.WeightRecordCorrectionRequest request) {
        return Map.of("ok", true, "data", corrections.correctWeight(id, request));
    }

    @PostMapping("/submissions/{id}/decision")
    public Map<String, Object> decide(@PathVariable String id, @RequestBody DecisionRequest request) {
        return Map.of("ok", true, "data", service.decide(id,
                request == null ? null : request.decision(),
                request == null ? null : request.reason()));
    }

    public record CommentRequest(@NotBlank @Size(max = 4000) String body) {}
    public record DecisionRequest(@NotBlank String decision, @Size(max = 2000) String reason) {}
    public record OverrideRequest(@NotBlank @Size(max = 1000) String reason) {}
}
