package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.service.ReportService;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service){this.service=service;}
    @GetMapping("/farm") public Map<String,Object> farm(@RequestParam(required=false) LocalDate asOf){return Map.of("ok",true,"data",service.farm(asOf));}
    @GetMapping("/batches/{id}") public Map<String,Object> batch(@PathVariable String id,@RequestParam(required=false) LocalDate asOf){return Map.of("ok",true,"data",service.batch(id,asOf));}
}
