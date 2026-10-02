package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.EggCollectionRequest;
import com.grantinofarms.poultry.dto.EggCollectionResponse;
import com.grantinofarms.poultry.dto.EggInventoryResponse;
import com.grantinofarms.poultry.dto.EggSaleRequest;
import com.grantinofarms.poultry.dto.EggSaleResponse;
import com.grantinofarms.poultry.service.EggManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/batches/{batchId}/eggs")
public class EggManagementController {
    private final EggManagementService service;

    public EggManagementController(EggManagementService service) {
        this.service = service;
    }

    @PostMapping("/collections")
    public EggCollectionResponse addCollection(@PathVariable String batchId,
                                               @Valid @RequestBody EggCollectionRequest request) {
        return service.addCollection(batchId, request);
    }

    @GetMapping("/collections")
    public List<EggCollectionResponse> collections(@PathVariable String batchId,
                                                   @RequestParam(required = false) LocalDate asOf) {
        return service.collections(batchId, asOf == null ? LocalDate.now() : asOf);
    }

    @PostMapping("/sales")
    public EggSaleResponse addSale(@PathVariable String batchId,
                                   @Valid @RequestBody EggSaleRequest request) {
        return service.addSale(batchId, request);
    }

    @GetMapping("/sales")
    public List<EggSaleResponse> sales(@PathVariable String batchId,
                                      @RequestParam(required = false) LocalDate asOf) {
        return service.sales(batchId, asOf == null ? LocalDate.now() : asOf);
    }

    @GetMapping("/inventory")
    public EggInventoryResponse inventory(@PathVariable String batchId,
                                          @RequestParam(required = false) LocalDate asOf) {
        return service.inventory(batchId, asOf == null ? LocalDate.now() : asOf);
    }
}
