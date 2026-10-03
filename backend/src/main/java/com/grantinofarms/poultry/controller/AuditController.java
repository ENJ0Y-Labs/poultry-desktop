package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private final AuditRepository audit;
    private final FarmRepository farms;
    public AuditController(AuditRepository audit, FarmRepository farms) { this.audit = audit; this.farms = farms; }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "100") int limit) {
        var farm = farms.findActive();
        return Map.of("ok", true, "data", farm == null ? java.util.List.of() : audit.find(farm.id(), limit));
    }
}
