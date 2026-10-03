package com.grantinofarms.poultry.controller;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.HealthManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/batches/{batchId}")
public class HealthManagementController {
 private final HealthManagementService service;
 public HealthManagementController(HealthManagementService service){this.service=service;}
 @PostMapping("/health") public Map<String,Object> addHealth(@PathVariable String batchId,@Valid @RequestBody HealthRecordRequest r){return ok(service.addHealth(batchId,r));}
 @GetMapping("/health") public Map<String,Object> health(@PathVariable String batchId){return ok(service.health(batchId));}
 @PostMapping("/drugs") public Map<String,Object> addDrug(@PathVariable String batchId,@Valid @RequestBody DrugRecordRequest r){return ok(service.addDrug(batchId,r));}
 @GetMapping("/drugs") public Map<String,Object> drugs(@PathVariable String batchId){return ok(service.drugs(batchId));}
 @PostMapping("/vaccinations") public Map<String,Object> addVaccination(@PathVariable String batchId,@Valid @RequestBody VaccinationRecordRequest r){return ok(service.addVaccination(batchId,r));}
 @GetMapping("/vaccinations") public Map<String,Object> vaccinations(@PathVariable String batchId){return ok(service.vaccinations(batchId));}
 private Map<String,Object> ok(Object data){return Map.of("ok",true,"data",data);}
}