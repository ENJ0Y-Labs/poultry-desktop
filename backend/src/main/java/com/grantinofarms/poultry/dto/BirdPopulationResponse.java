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
        int currentBirds,
        String countStatus
) {
    public BirdPopulationResponse(String batchId, LocalDate asOf, int initialBirds, int mortality,
                                  int culling, int sold, int transfersIn, int transfersOut, int currentBirds) {
        this(batchId, asOf, initialBirds, mortality, culling, sold, transfersIn, transfersOut, currentBirds, "VERIFIED");
    }
}
