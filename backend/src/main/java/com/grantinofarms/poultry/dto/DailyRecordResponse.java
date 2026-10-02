package com.grantinofarms.poultry.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyRecordResponse(
        String id,
        String batchId,
        LocalDate date,
        int birds,
        int mortality,
        int culling,
        List<DailyFeedUsageResponse> feed,
        List<WaterUsageResponse> water,
        long totalWaterUnits,
        List<DailyPopulationEventResponse> populationEvents,
        String notes
) {}
