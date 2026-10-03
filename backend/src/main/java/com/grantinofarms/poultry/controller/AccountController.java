package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.AccountUpdateRequest;
import com.grantinofarms.poultry.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }

    @GetMapping
    Map<String,Object> get() { return Map.of("ok", true, "data", service.current()); }

    @PutMapping
    Map<String,Object> update(@Valid @RequestBody AccountUpdateRequest request) {
        return Map.of("ok", true, "data", service.update(request));
    }
}
