package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.dto.HouseUpdateRequest;
import com.grantinofarms.poultry.service.HouseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/houses")
public class HouseAliasController {
    private final HouseService service;
    public HouseAliasController(HouseService service){this.service=service;}
    @GetMapping public Map<String,Object> list(){return Map.of("ok",true,"data",service.list());}
    @PostMapping public Map<String,Object> create(@Valid @RequestBody HouseCreateRequest request){return Map.of("ok",true,"data",service.create(request));}
    @PutMapping("/{id}") public Map<String,Object> update(@PathVariable String id,@Valid @RequestBody HouseUpdateRequest request){return Map.of("ok",true,"data",service.update(id,request));}
}
