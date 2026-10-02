package com.grantinofarms.poultry.dto;

public record EggInventoryResponse(
        String batchId,
        int goodCollected,
        int crackedCollected,
        int goodSold,
        int goodRemaining,
        int totalCollected
) {}
