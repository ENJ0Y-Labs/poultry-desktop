package com.grantinofarms.poultry.controller;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {
 private final ExpenseService service;
 public ExpenseController(ExpenseService service){this.service=service;}
 @PostMapping public Map<String,Object> create(@Valid @RequestBody ExpenseRequest r){return ok(service.create(r));}
 @GetMapping public Map<String,Object> list(@RequestParam(required=false) String batchId){return ok(service.list(batchId));}
 private Map<String,Object> ok(Object data){return Map.of("ok",true,"data",data);}
}