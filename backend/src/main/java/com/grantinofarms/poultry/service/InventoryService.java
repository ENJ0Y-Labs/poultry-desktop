package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.InventoryItemCreateRequest;
import com.grantinofarms.poultry.dto.InventoryItemResponse;
import com.grantinofarms.poultry.dto.InventoryMovementRequest;
import com.grantinofarms.poultry.dto.InventoryMovementResponse;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.BatchRepository;
import com.grantinofarms.poultry.repository.InventoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {
    private static final List<String> CATEGORIES = List.of("DRUG", "VACCINE", "SUPPLY");
    private static final List<String> MOVEMENT_TYPES =
            List.of("RECEIVE", "ISSUE", "ADJUST_IN", "ADJUST_OUT", "WASTE");

    private final InventoryRepository repository;
    private final BatchRepository batchRepository;
    private final AuditRepository auditRepository;

    public InventoryService(InventoryRepository repository, BatchRepository batchRepository,
                            AuditRepository auditRepository) {
        this.repository = repository;
        this.batchRepository = batchRepository;
        this.auditRepository = auditRepository;
    }

    public List<InventoryItemResponse> listItems() {
        return repository.findItems(repository.farmId());
    }

    @Transactional
    public InventoryItemResponse createItem(InventoryItemCreateRequest request) {
        String farmId = repository.farmId();
        String name = clean(request.name());
        String category = normalize(request.category());
        String unit = clean(request.unit());
        BigDecimal reorderLevel = normalizeReorderLevel(request.reorderLevel() == null ? BigDecimal.ZERO : request.reorderLevel());

        if (!CATEGORIES.contains(category)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVENTORY_CATEGORY",
                    "Inventory category must be DRUG, VACCINE, or SUPPLY.");
        }

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        try {
            repository.insertItem(id, farmId, name, category, unit, reorderLevel, now);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_INVENTORY_ITEM",
                    "An inventory item with this name already exists.");
        }

        auditRepository.append(farmId, "CREATE", "INVENTORY_ITEM", id, null, null,
                String.format("{\"name\":\"%s\",\"category\":\"%s\",\"unit\":\"%s\"}",
                        name, category, unit), now);
        return repository.findItem(id, farmId);
    }

    @Transactional
    public InventoryItemResponse archiveItem(String id) {
        String farmId = repository.farmId();
        InventoryItemResponse item = requireActiveItem(id, farmId);
        repository.archiveItem(id, farmId, Instant.now().toString());
        return new InventoryItemResponse(item.id(), item.farmId(), item.name(), item.category(),
                item.unit(), item.reorderLevel(), "ARCHIVED", item.quantityOnHand());
    }

    @Transactional
    public InventoryMovementResponse recordMovement(String itemId, InventoryMovementRequest request) {
        String farmId = repository.farmId();
        InventoryItemResponse item = requireActiveItem(itemId, farmId);
        String type = normalize(request.movementType());
        if (!MOVEMENT_TYPES.contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVENTORY_MOVEMENT",
                    "Movement type must be RECEIVE, ISSUE, ADJUST_IN, ADJUST_OUT, or WASTE.");
        }

        BigDecimal quantity = normalizeQuantity(request.quantity());
        String reason = cleanRequired(request.reason(), "Inventory movement reason is required.");
        String source = cleanRequired(request.source(), "Inventory movement source is required.");

        String batchId = cleanNullable(request.batchId());
        if (batchId != null && !batchRepository.belongsToFarm(batchId, farmId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BATCH",
                    "Batch does not belong to the active farm.");
        }

        BigDecimal current = repository.quantityOnHand(itemId, farmId);
        if (reducesStock(type) && current.compareTo(quantity) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_INVENTORY",
                    "The movement would make inventory negative. Current stock: " + current + " " + item.unit());
        }

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        repository.insertMovement(id, farmId, itemId, request.movementDate(), type,
                quantity, reason, source, batchId, now);

        auditRepository.append(farmId, "CREATE", "INVENTORY_MOVEMENT", id, reason, null,
                String.format("{\"itemId\":\"%s\",\"movementType\":\"%s\",\"quantity\":\"%s\",\"source\":\"%s\"}",
                        itemId, type, quantity.toPlainString(), source), now);

        return repository.findMovements(farmId, itemId, null).stream()
                .filter(m -> m.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Inventory movement was created but could not be read."));
    }

    public List<InventoryMovementResponse> movements(String itemId, LocalDate asOf) {
        String farmId = repository.farmId();
        if (itemId != null && repository.findItem(itemId, farmId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "INVENTORY_ITEM_NOT_FOUND", "Inventory item not found.");
        }
        return repository.findMovements(farmId, itemId, asOf);
    }

    private InventoryItemResponse requireActiveItem(String id, String farmId) {
        InventoryItemResponse item = repository.findActiveItem(id, farmId);
        if (item == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "INVENTORY_ITEM_NOT_FOUND",
                    "Active inventory item not found.");
        }
        return item;
    }

    private boolean reducesStock(String type) {
        return type.equals("ISSUE") || type.equals("ADJUST_OUT") || type.equals("WASTE");
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVENTORY_QUANTITY",
                    "Inventory quantity must be greater than zero.");
        }
        return quantity.stripTrailingZeros();
    }

    private BigDecimal normalizeReorderLevel(BigDecimal quantity) {
        if (quantity == null || quantity.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REORDER_LEVEL",
                    "Inventory reorder level cannot be negative.");
        }
        return quantity.stripTrailingZeros();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String cleanRequired(String value, String message) {
        String cleaned = clean(value);
        if (cleaned == null || cleaned.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVENTORY_MOVEMENT", message);
        }
        return cleaned;
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
