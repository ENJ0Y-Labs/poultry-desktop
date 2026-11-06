package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
public record PopulationConfidenceRequest(@NotBlank String reason) {}
