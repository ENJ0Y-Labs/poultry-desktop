package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.service.FarmIncidentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/incidents")
public class FarmIncidentController {
    private final FarmIncidentService incidents;

    public FarmIncidentController(FarmIncidentService incidents) { this.incidents = incidents; }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("ok", true, "data", incidents.list());
    }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody FarmIncidentService.IncidentRequest request) {
        return Map.of("ok", true, "data", incidents.create(request));
    }

    @PostMapping("/{id}/acknowledge")
    public Map<String, Object> acknowledge(@PathVariable String id) {
        return Map.of("ok", true, "data", incidents.acknowledge(id));
    }

    @PostMapping("/{id}/resolve")
    public Map<String, Object> resolve(@PathVariable String id, @Valid @RequestBody ResolveRequest request) {
        return Map.of("ok", true, "data", incidents.resolve(id, request.notes()));
    }

    public record ResolveRequest(@NotBlank @Size(max = 2000) String notes) {}
}
