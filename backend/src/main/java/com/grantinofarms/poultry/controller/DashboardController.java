package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.service.DashboardService;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service){this.service=service;}
    @GetMapping("/dashboard") public Map<String,Object> farm(@RequestParam(required=false) LocalDate asOf){return Map.of("ok",true,"data",service.farm(asOf));}
    @GetMapping("/batches/{id}/dashboard") public Map<String,Object> batch(@PathVariable String id,@RequestParam(required=false) LocalDate asOf){return Map.of("ok",true,"data",service.batch(id,asOf));}
}
