package com.grantinofarms.poultry.controller;
import com.grantinofarms.poultry.dto.BirdAdditionalCostRequest;
import com.grantinofarms.poultry.service.BirdCostService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/batches/{batchId}")
public class BirdCostController {
 private final BirdCostService birdCostService;
 public BirdCostController(BirdCostService birdCostService){this.birdCostService=birdCostService;}
 @GetMapping("/costs") public Map<String,Object> costs(@PathVariable String batchId,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf){return Map.of("ok",true,"data",birdCostService.get(batchId,asOf));}
 @PostMapping("/costs") public Map<String,Object> addCost(@PathVariable String batchId,@Valid @RequestBody BirdAdditionalCostRequest r){return Map.of("ok",true,"data",birdCostService.addAdditionalCost(batchId,r));}
}