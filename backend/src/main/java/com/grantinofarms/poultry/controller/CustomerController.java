package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.CustomerCreateRequest;
import com.grantinofarms.poultry.dto.CustomerResponse;
import com.grantinofarms.poultry.dto.CustomerUpdateRequest;
import com.grantinofarms.poultry.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping
    public CustomerResponse create(@Valid @RequestBody CustomerCreateRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<CustomerResponse> list() {
        return service.list();
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable String id,
                                   @Valid @RequestBody CustomerUpdateRequest request) {
        return service.update(id, request);
    }
}
