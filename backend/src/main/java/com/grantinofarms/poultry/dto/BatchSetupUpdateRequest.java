package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;

public record BatchSetupUpdateRequest(
        @NotBlank String houseId,
        String supplierId
) {}
