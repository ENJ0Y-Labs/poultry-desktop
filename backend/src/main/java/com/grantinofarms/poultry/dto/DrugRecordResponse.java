package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record DrugRecordResponse(
        String id,
        String batchId,
        LocalDate recordDate,
        String drug,
        int quantity,
        long costMinor,
        String reason
) {}
