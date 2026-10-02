package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FarmCreateRequest(
        @NotBlank String name,
        String location,
        @NotBlank String timezone,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency
) {}
