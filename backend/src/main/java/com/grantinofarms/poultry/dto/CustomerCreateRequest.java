package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerCreateRequest(
        @NotBlank String name,
        String phone,
        String notes
) {}
