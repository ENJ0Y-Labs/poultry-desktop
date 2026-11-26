package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminUserRoleRequest(
        @NotBlank @Pattern(regexp = "WORKER|ADMINISTRATOR|MANAGER") String role
) {}
