package com.grantinofarms.poultry.controller;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.BroilerProductionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/batches/{batchId}/broiler")
public class BroilerProductionController {
 private final BroilerProductionService service;
 public BroilerProductionController(BroilerProductionService service){this.service=service;}
 @PostMapping("/weights") public Map<String,Object> addWeight(@PathVariable String batchId,@Valid @RequestBody WeightRecordRequest r){return ok(service.addWeight(batchId,r));}
 @GetMapping("/weights") public Map<String,Object> weights(@PathVariable String batchId,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf){return ok(service.weights(batchId,asOf));}
 @GetMapping("/growth") public Map<String,Object> growth(@PathVariable String batchId,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf){return ok(service.growth(batchId,asOf));}
 @PostMapping("/sales") public Map<String,Object> sale(@PathVariable String batchId,@Valid @RequestBody BirdSaleRequest r){return ok(service.addSale(batchId,r));}
 @GetMapping("/sales") public Map<String,Object> sales(@PathVariable String batchId,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf){return ok(service.sales(batchId,asOf));}
 private Map<String,Object> ok(Object data){return Map.of("ok",true,"data",data);}
}