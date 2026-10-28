package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CsvImportRequest(
        @NotBlank String module,
        @NotEmpty @Size(max = 5000) List<Map<String, String>> records
) {}
