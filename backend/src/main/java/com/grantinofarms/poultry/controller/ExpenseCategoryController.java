package com.grantinofarms.poultry.controller;
import com.grantinofarms.poultry.dto.ExpenseCategoryRequest;
import com.grantinofarms.poultry.service.ExpenseCategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/expense-categories")
public class ExpenseCategoryController {
    private final ExpenseCategoryService service;
    public ExpenseCategoryController(ExpenseCategoryService service){this.service=service;}
    @GetMapping Map<String,Object> list(){return Map.of("ok",true,"data",service.list());}
    @PostMapping Map<String,Object> create(@Valid @RequestBody ExpenseCategoryRequest request){return Map.of("ok",true,"data",service.create(request));}
    @PostMapping("/{id}/archive") Map<String,Object> archive(@PathVariable String id){return Map.of("ok",true,"data",service.archive(id));}
}
