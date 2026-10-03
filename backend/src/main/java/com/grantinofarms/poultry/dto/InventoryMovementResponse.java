package com.grantinofarms.poultry.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryMovementResponse(
        String id,
        String inventoryItemId,
        String itemName,
        String category,
        LocalDate movementDate,
        String movementType,
        BigDecimal quantity,
        String reason,
        String source,
        String batchId,
        String createdAt
) {}
