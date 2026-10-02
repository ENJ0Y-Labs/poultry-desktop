package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;

public record BatchReopenRequest(
        @NotBlank String reason
) {}
