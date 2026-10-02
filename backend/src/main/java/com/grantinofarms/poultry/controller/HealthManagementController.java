package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.HealthManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/batches/{batchId}")
public class HealthManagementController {
    private final HealthManagementService service;

    public HealthManagementController(HealthManagementService service) {
        this.service = service;
    }

    @PostMapping("/health")
    public HealthRecordResponse addHealth(@PathVariable String batchId,
                                          @Valid @RequestBody HealthRecordRequest request) {
        return service.addHealth(batchId, request);
    }

    @GetMapping("/health")
    public List<HealthRecordResponse> health(@PathVariable String batchId) {
        return service.health(batchId);
    }

    @PostMapping("/drugs")
    public DrugRecordResponse addDrug(@PathVariable String batchId,
                                      @Valid @RequestBody DrugRecordRequest request) {
        return service.addDrug(batchId, request);
    }

    @GetMapping("/drugs")
    public List<DrugRecordResponse> drugs(@PathVariable String batchId) {
        return service.drugs(batchId);
    }

    @PostMapping("/vaccinations")
    public VaccinationRecordResponse addVaccination(@PathVariable String batchId,
                                                    @Valid @RequestBody VaccinationRecordRequest request) {
        return service.addVaccination(batchId, request);
    }

    @GetMapping("/vaccinations")
    public List<VaccinationRecordResponse> vaccinations(@PathVariable String batchId) {
        return service.vaccinations(batchId);
    }
}
