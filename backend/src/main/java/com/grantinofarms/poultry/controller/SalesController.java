package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.SaleResponse;
import com.grantinofarms.poultry.service.SalesService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {
    private final SalesService service;

    public SalesController(SalesService service) {
        this.service = service;
    }

    @GetMapping
    public List<SaleResponse> list(
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String saleType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.list(batchId, customerId, saleType, asOf);
    }
}
