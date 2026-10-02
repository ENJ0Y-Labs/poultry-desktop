package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record BirdPopulationResponse(
        String batchId,
        LocalDate asOf,
        int initialBirds,
        int mortality,
        int culling,
        int sold,
        int transfersIn,
        int transfersOut,
        int currentBirds
) {}
