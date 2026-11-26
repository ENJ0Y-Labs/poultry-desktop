package com.grantinofarms.poultry.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportSubmissionRequest(
        @NotBlank @Size(max = 80) String reportType,
        @NotBlank @Size(max = 160) String title,
        @NotNull JsonNode payload
) {}
