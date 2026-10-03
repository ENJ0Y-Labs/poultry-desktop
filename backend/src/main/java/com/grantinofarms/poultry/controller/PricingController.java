package com.grantinofarms.poultry.controller;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.PricingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/pricing")
public class PricingController {
 private final PricingService service;
 public PricingController(PricingService service){this.service=service;}
 @GetMapping("/settings") public Map<String,Object> settings(){return Map.of("ok",true,"data",service.settings());}
 @PutMapping("/settings") public Map<String,Object> updateSettings(@Valid @RequestBody PricingSettingsRequest r){return Map.of("ok",true,"data",service.updateSettings(r));}
 @GetMapping("/batches/{batchId}") public Map<String,Object> price(@PathVariable String batchId,@RequestParam int quantity,@RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate asOf){return Map.of("ok",true,"data",service.price(batchId,quantity,asOf));}
}