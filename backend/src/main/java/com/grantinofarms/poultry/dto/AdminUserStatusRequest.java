package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminUserStatusRequest(
        @NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED") String status
) {}
