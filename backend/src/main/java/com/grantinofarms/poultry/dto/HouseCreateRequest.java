package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;

public record HouseCreateRequest(
        @NotBlank String name,
        @NotBlank String code,
        String notes
) {}
