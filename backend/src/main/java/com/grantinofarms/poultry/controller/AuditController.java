package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.domain.ReportDateRange;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.service.CurrentUserContext;
import com.grantinofarms.poultry.service.RoleAuthorization;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private final AuditRepository audit;
    private final FarmRepository farms;

    public AuditController(AuditRepository audit, FarmRepository farms) {
        this.audit = audit;
        this.farms = farms;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        RoleAuthorization.requireAny("MANAGER", "ADMINISTRATOR");
        var farm = farms.findActive();
        if (farm == null) return Map.of("ok", true, "data", List.of());
        String start = null;
        String end = null;
        if (from != null || to != null) {
            ReportDateRange range = ReportDateRange.require(from, to);
            ZoneId zone = ZoneId.of(farm.timezone());
            start = range.from().atStartOfDay(zone).toInstant().toString();
            end = range.to().plusDays(1).atStartOfDay(zone).toInstant().toString();
        }
        List<Map<String, Object>> rows = "ADMINISTRATOR".equals(CurrentUserContext.getRole())
                ? audit.findAccountEvents(farm.id(), limit, start, end)
                : audit.find(farm.id(), limit, start, end);
        return Map.of("ok", true, "data", rows);
    }
}
