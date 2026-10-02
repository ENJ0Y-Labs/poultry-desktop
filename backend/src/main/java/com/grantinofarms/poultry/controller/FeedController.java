package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.BatchFeedCostResponse;
import com.grantinofarms.poultry.dto.FeedInventoryResponse;
import com.grantinofarms.poultry.dto.FeedPurchaseRequest;
import com.grantinofarms.poultry.dto.FeedPurchaseResponse;
import com.grantinofarms.poultry.dto.FeedTypeCreateRequest;
import com.grantinofarms.poultry.dto.FeedTypeResponse;
import com.grantinofarms.poultry.dto.FeedUsageRequest;
import com.grantinofarms.poultry.dto.FeedUsageResponse;
import com.grantinofarms.poultry.service.FeedService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {
    private final FeedService service;

    public FeedController(FeedService service) {
        this.service = service;
    }

    @GetMapping("/types")
    public List<FeedTypeResponse> types() {
        return service.types();
    }

    @PostMapping("/types")
    public FeedTypeResponse createType(@Valid @RequestBody FeedTypeCreateRequest request) {
        return service.createType(request);
    }

    @PostMapping("/types/{id}/archive")
    public FeedTypeResponse archiveType(@PathVariable String id) {
        return service.archiveType(id);
    }

    @PostMapping("/purchases")
    public FeedPurchaseResponse purchase(@Valid @RequestBody FeedPurchaseRequest request) {
        return service.purchase(request);
    }

    @GetMapping("/inventory")
    public List<FeedInventoryResponse> inventory(@RequestParam(required = false) LocalDate asOf) {
        return service.inventory(asOf);
    }

    @PostMapping("/batches/{batchId}/usage")
    public FeedUsageResponse use(@PathVariable String batchId,
                                 @Valid @RequestBody FeedUsageRequest request) {
        return service.use(batchId, request);
    }

    @GetMapping("/batches/{batchId}/cost")
    public BatchFeedCostResponse batchCost(@PathVariable String batchId,
                                           @RequestParam(required = false) LocalDate asOf) {
        return service.batchCost(batchId, asOf);
    }
}
