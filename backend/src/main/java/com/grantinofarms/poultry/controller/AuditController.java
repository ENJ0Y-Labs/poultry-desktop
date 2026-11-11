package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.domain.ReportDateRange;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private final AuditRepository audit;
    private final FarmRepository farms;
    public AuditController(AuditRepository audit, FarmRepository farms) { this.audit = audit; this.farms = farms; }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        var farm = farms.findActive();
        if (farm == null) return Map.of("ok", true, "data", java.util.List.of());
        if (from == null && to == null) return Map.of("ok", true, "data", audit.find(farm.id(), limit));
        ReportDateRange range = ReportDateRange.require(from, to);
        ZoneId zone = ZoneId.of(farm.timezone());
        String start = range.from().atStartOfDay(zone).toInstant().toString();
        String end = range.to().atTime(java.time.LocalTime.MAX).atZone(zone).toInstant().toString();
        return Map.of("ok", true, "data", audit.find(farm.id(), limit, start, end));
    }
}
