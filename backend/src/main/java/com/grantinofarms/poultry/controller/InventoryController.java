package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.InventoryItemCreateRequest;
import com.grantinofarms.poultry.dto.InventoryItemResponse;
import com.grantinofarms.poultry.dto.InventoryMovementRequest;
import com.grantinofarms.poultry.dto.InventoryMovementResponse;
import com.grantinofarms.poultry.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/items")
    public List<InventoryItemResponse> items() {
        return service.listItems();
    }

    @PostMapping("/items")
    public InventoryItemResponse createItem(@Valid @RequestBody InventoryItemCreateRequest request) {
        return service.createItem(request);
    }

    @PostMapping("/items/{id}/archive")
    public InventoryItemResponse archiveItem(@PathVariable String id) {
        return service.archiveItem(id);
    }

    @GetMapping("/movements")
    public List<InventoryMovementResponse> movements(
            @RequestParam(required = false) String itemId,
            @RequestParam(required = false) LocalDate asOf) {
        return service.movements(itemId, asOf);
    }

    @PostMapping("/items/{id}/movements")
    public InventoryMovementResponse movement(@PathVariable String id,
                                              @Valid @RequestBody InventoryMovementRequest request) {
        return service.recordMovement(id, request);
    }
}
