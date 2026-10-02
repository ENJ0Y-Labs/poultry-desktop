package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;

public record FeedTypeCreateRequest(
        @NotBlank String name,
        @NotBlank String unit,
        @NotBlank String applicableType
) {}
