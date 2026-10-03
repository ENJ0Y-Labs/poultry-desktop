package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.SupplierCreateRequest;
import com.grantinofarms.poultry.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {
    private final SupplierService service;
    public SupplierController(SupplierService service){this.service=service;}
    @GetMapping public Map<String,Object> list(){return Map.of("ok",true,"data",service.list());}
    @PostMapping public Map<String,Object> create(@Valid @RequestBody SupplierCreateRequest request){return Map.of("ok",true,"data",service.create(request));}
    @PostMapping("/{id}/archive") public Map<String,Object> archive(@PathVariable String id){return Map.of("ok",true,"data",service.archive(id));}
}
