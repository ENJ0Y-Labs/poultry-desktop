package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.AdminUserCreateRequest;
import com.grantinofarms.poultry.dto.AdminUserPasswordResetRequest;
import com.grantinofarms.poultry.dto.AdminUserRoleRequest;
import com.grantinofarms.poultry.dto.AdminUserStatusRequest;
import com.grantinofarms.poultry.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("ok", true, "data", service.list());
    }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody AdminUserCreateRequest request) {
        return Map.of("ok", true, "data", service.create(request));
    }

    @PatchMapping("/{id}/role")
    public Map<String, Object> changeRole(@PathVariable String id,
                                          @Valid @RequestBody AdminUserRoleRequest request) {
        return Map.of("ok", true, "data", service.changeRole(id, request.role()));
    }

    @PatchMapping("/{id}/status")
    public Map<String, Object> changeStatus(@PathVariable String id,
                                            @Valid @RequestBody AdminUserStatusRequest request) {
        return Map.of("ok", true, "data", service.changeStatus(id, request.status()));
    }

    @PostMapping("/{id}/password-reset")
    public Map<String, Object> resetPassword(@PathVariable String id,
                                             @Valid @RequestBody AdminUserPasswordResetRequest request) {
        return Map.of("ok", true, "data", service.resetPassword(id, request.password()));
    }
}
