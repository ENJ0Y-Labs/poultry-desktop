package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record HealthRecordResponse(
        String id,
        String batchId,
        LocalDate recordDate,
        String conditionProblem,
        String description,
        String action
) {}
