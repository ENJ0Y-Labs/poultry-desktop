package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record VaccinationRecordResponse(
        String id,
        String batchId,
        LocalDate recordDate,
        String vaccine,
        String dose,
        int quantity,
        String notes
) {}
