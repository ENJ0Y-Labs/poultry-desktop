package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.ExpenseRequest;
import com.grantinofarms.poultry.dto.ExpenseResponse;
import com.grantinofarms.poultry.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {
    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @PostMapping
    public ExpenseResponse create(@Valid @RequestBody ExpenseRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<ExpenseResponse> list(@RequestParam(required = false) String batchId) {
        return service.list(batchId);
    }
}
