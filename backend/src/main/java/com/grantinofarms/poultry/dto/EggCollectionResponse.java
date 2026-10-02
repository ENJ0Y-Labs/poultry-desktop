package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record EggCollectionResponse(
        String id,
        String batchId,
        LocalDate recordDate,
        int good,
        int cracked,
        int total,
        String notes
) {}
