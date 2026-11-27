package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.service.ReportingConfigurationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/review/reporting")
public class ReportingConfigurationController {
    private final ReportingConfigurationService reporting;

    public ReportingConfigurationController(ReportingConfigurationService reporting) {
        this.reporting = reporting;
    }

    @GetMapping("/schedules")
    public Map<String, Object> schedules() {
        return Map.of("ok", true, "data", reporting.schedules());
    }

    @PostMapping("/schedules")
    public Map<String, Object> createSchedule(@RequestBody ReportingConfigurationService.ScheduleRequest request) {
        return Map.of("ok", true, "data", reporting.createSchedule(request));
    }

    @PatchMapping("/schedules/{id}/enabled")
    public Map<String, Object> setScheduleEnabled(@PathVariable String id, @RequestBody EnabledRequest request) {
        reporting.setScheduleEnabled(id, request.enabled());
        return Map.of("ok", true, "data", Map.of("enabled", request.enabled()));
    }

    @GetMapping("/thresholds")
    public Map<String, Object> thresholds() {
        return Map.of("ok", true, "data", reporting.thresholds());
    }

    @PostMapping("/thresholds")
    public Map<String, Object> createThreshold(@RequestBody ReportingConfigurationService.ThresholdRequest request) {
        return Map.of("ok", true, "data", reporting.createThreshold(request));
    }

    @PatchMapping("/thresholds/{id}/enabled")
    public Map<String, Object> setThresholdEnabled(@PathVariable String id, @RequestBody EnabledRequest request) {
        reporting.setThresholdEnabled(id, request.enabled());
        return Map.of("ok", true, "data", Map.of("enabled", request.enabled()));
    }

    @GetMapping("/missing")
    public Map<String, Object> missingReports() {
        return Map.of("ok", true, "data", reporting.missingReports());
    }

    public record EnabledRequest(boolean enabled) {}
}
