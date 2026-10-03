package com.grantinofarms.poultry.controller;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.EggManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/batches/{batchId}/eggs")
public class EggManagementController {
 private final EggManagementService service;
 public EggManagementController(EggManagementService service){this.service=service;}
 @PostMapping("/collections") public Map<String,Object> addCollection(@PathVariable String batchId,@Valid @RequestBody EggCollectionRequest r){return ok(service.addCollection(batchId,r));}
 @GetMapping("/collections") public Map<String,Object> collections(@PathVariable String batchId,@RequestParam(required=false) LocalDate asOf){return ok(service.collections(batchId,asOf==null?LocalDate.now():asOf));}
 @PostMapping("/sales") public Map<String,Object> addSale(@PathVariable String batchId,@Valid @RequestBody EggSaleRequest r){return ok(service.addSale(batchId,r));}
 @GetMapping("/sales") public Map<String,Object> sales(@PathVariable String batchId,@RequestParam(required=false) LocalDate asOf){return ok(service.sales(batchId,asOf==null?LocalDate.now():asOf));}
 @GetMapping("/inventory") public Map<String,Object> inventory(@PathVariable String batchId,@RequestParam(required=false) LocalDate asOf){return ok(service.inventory(batchId,asOf==null?LocalDate.now():asOf));}
 private Map<String,Object> ok(Object data){return Map.of("ok",true,"data",data);}
}