package com.grantinofarms.poultry.controller;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.BirdPopulationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/batches/{batchId}")
public class BirdPopulationController {
 private final BirdPopulationService populationService;
 public BirdPopulationController(BirdPopulationService populationService){this.populationService=populationService;}
 @GetMapping("/population") public Map<String,Object> population(@PathVariable String batchId,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf){return ok(populationService.get(batchId,asOf));}
 @PostMapping("/mortality") public Map<String,Object> mortality(@PathVariable String batchId,@Valid @RequestBody BirdPopulationEventRequest r){return ok(populationService.addMortality(batchId,r));}
 @PostMapping("/culling") public Map<String,Object> culling(@PathVariable String batchId,@Valid @RequestBody BirdPopulationEventRequest r){return ok(populationService.addCulling(batchId,r));}
 @PostMapping("/bird-sales") public Map<String,Object> sale(@PathVariable String batchId,@Valid @RequestBody BirdPopulationEventRequest r){return ok(populationService.addSale(batchId,r));}
 @PostMapping("/transfers") public Map<String,Object> transfer(@PathVariable String batchId,@Valid @RequestBody BirdTransferRequest r){return ok(populationService.transfer(batchId,r));}
 private Map<String,Object> ok(Object data){return Map.of("ok",true,"data",data);}
}