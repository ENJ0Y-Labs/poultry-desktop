package com.grantinofarms.poultry.controller;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
 private final CustomerService service;
 public CustomerController(CustomerService service){this.service=service;}
 @PostMapping public Map<String,Object> create(@Valid @RequestBody CustomerCreateRequest r){return ok(service.create(r));}
 @GetMapping public Map<String,Object> list(){return ok(service.list());}
 @PutMapping("/{id}") public Map<String,Object> update(@PathVariable String id,@Valid @RequestBody CustomerUpdateRequest r){return ok(service.update(id,r));}
 private Map<String,Object> ok(Object data){return Map.of("ok",true,"data",data);}
}