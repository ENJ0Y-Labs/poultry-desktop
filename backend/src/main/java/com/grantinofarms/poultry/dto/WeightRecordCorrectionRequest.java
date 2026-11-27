package com.grantinofarms.poultry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WeightRecordCorrectionRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotNull @Valid WeightRecordRequest replacement
) {}
