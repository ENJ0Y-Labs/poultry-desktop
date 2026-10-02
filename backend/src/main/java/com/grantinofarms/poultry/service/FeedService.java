package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.domain.FeedCostCalculator;
import com.grantinofarms.poultry.domain.FeedPurchaseLot;
import com.grantinofarms.poultry.domain.FeedUsageEvent;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.FeedRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FeedService {
    private final FeedRepository repository;
    private final BirdPopulationService populationService;
    private final BirdCostService birdCostService;
    private final AuditRepository auditRepository;

    public FeedService(FeedRepository repository, BirdPopulationService populationService,
                       BirdCostService birdCostService, AuditRepository auditRepository) {
        this.repository = repository;
        this.populationService = populationService;
        this.birdCostService = birdCostService;
        this.auditRepository = auditRepository;
    }

    public List<FeedTypeResponse> types() {
        return repository.findTypes(repository.farmId());
    }

    @Transactional
    public FeedTypeResponse createType(FeedTypeCreateRequest request) {
        String scope = normalizeScope(request.applicableType());
        String unit = clean(request.unit());
        String name = clean(request.name());
        String farmId = repository.farmId();
        if (!List.of("LAYER", "BROILER", "BOTH").contains(scope)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_SCOPE",
                    "Feed type must apply to LAYER, BROILER, or BOTH.");
        }
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        try {
            repository.insertType(id, farmId, name, unit, scope, now);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_FEED_TYPE",
                    "A feed type with this name already exists.");
        }
        auditRepository.append(farmId, "CREATE", "FEED_TYPE", id, null, null,
                String.format("{\"name\":\"%s\",\"unit\":\"%s\",\"applicableType\":\"%s\"}",
                        name, unit, scope), now);
        return repository.findType(id);
    }

    @Transactional
    public FeedTypeResponse archiveType(String id) {
        FeedTypeResponse type = requireType(id);
        repository.archiveType(id, Instant.now().toString());
        return new FeedTypeResponse(type.id(), type.name(), type.unit(), type.applicableType(), "ARCHIVED");
    }

    @Transactional
    public FeedPurchaseResponse purchase(FeedPurchaseRequest request) {
        FeedTypeResponse type = requireType(request.feedTypeId());
        if (!"ACTIVE".equals(type.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "FEED_TYPE_NOT_ACTIVE", "The feed type is archived.");
        }
        String farmId = repository.farmId();
        if (!farmId.equals(repository.typeFarmId(type.id()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_TYPE", "Feed type does not belong to the active farm.");
        }
        if (!type.unit().equalsIgnoreCase(clean(request.unit()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_UNIT", "Purchase unit must match the feed type unit.");
        }
        if (request.supplierId() != null && !request.supplierId().isBlank()
                && !repository.supplierBelongsToFarm(request.supplierId().trim(), farmId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUPPLIER", "Supplier/source does not belong to the active farm.");
        }
        LocalDate latestUsage = repository.latestUsageDate(type.id());
        if (latestUsage != null && request.purchaseDate().isBefore(latestUsage)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_PURCHASE_DATE",
                    "A feed purchase cannot be backdated before existing usage for that feed type.");
        }
        LocalDate latestPurchase = repository.latestPurchaseDate(type.id());
        if (latestPurchase != null && request.purchaseDate().isBefore(latestPurchase)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_PURCHASE_DATE",
                    "A feed purchase cannot be backdated before the latest purchase for that feed type.");
        }

        long quantityMilli = toMilli(request.quantity());
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        repository.insertPurchase(id, farmId, type.id(), cleanNullable(request.supplierId()),
                request.purchaseDate(), quantityMilli, type.unit(), request.totalCostMinor(), now);
        repository.insertExpense(UUID.randomUUID().toString(), farmId, request.totalCostMinor(),
                request.purchaseDate(), id, "Feed purchase: " + type.name(), now);
        auditRepository.append(farmId, "CREATE", "FEED_PURCHASE", id, null, null,
                String.format("{\"feedTypeId\":\"%s\",\"quantityMilli\":%d,\"totalCostMinor\":%d}",
                        type.id(), quantityMilli, request.totalCostMinor()), now);
        return new FeedPurchaseResponse(id, type.id(), type.name(), type.unit(),
                request.purchaseDate(), quantityMilli, request.totalCostMinor(),
                quantityMilli, request.totalCostMinor());
    }

    public List<FeedInventoryResponse> inventory(LocalDate asOf) {
        LocalDate effective = asOf == null ? LocalDate.now() : asOf;
        List<FeedInventoryResponse> result = new ArrayList<>();
        for (FeedTypeResponse type : types()) {
            List<FeedPurchaseLot> purchases = repository.findPurchases(type.id(), effective);
            List<FeedUsageEvent> usages = repository.findUsages(type.id(), effective);
            FeedCostCalculator.FifoSnapshot snapshot = FeedCostCalculator.calculate(purchases, usages, effective);
            long purchased = purchases.stream().mapToLong(FeedPurchaseLot::quantityMilli).reduce(0L, Math::addExact);
            result.add(new FeedInventoryResponse(type.id(), type.name(), type.unit(), purchased,
                    snapshot.consumedQuantityMilli(), snapshot.remainingQuantityMilli(),
                    snapshot.remainingCostMinor(), snapshot.consumedCostMinor()));
        }
        return result;
    }

    @Transactional
    public FeedUsageResponse use(String batchId, FeedUsageRequest request) {
        String farmId = requireBatch(batchId);
        String batchType = repository.batchType(batchId);
        if (!"ACTIVE".equals(repository.batchStatus(batchId))) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_ACTIVE",
                    "Feed usage cannot be recorded for a non-ACTIVE batch.");
        }

        FeedTypeResponse type = requireType(request.feedTypeId());
        if (!farmId.equals(repository.typeFarmId(type.id()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_TYPE",
                    "Feed type does not belong to the batch farm.");
        }
        if (!isApplicable(type.applicableType(), batchType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FEED_TYPE_NOT_APPLICABLE",
                    "This feed type is not configured for the batch type.");
        }
        if (!type.unit().equalsIgnoreCase(clean(request.unit()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_UNIT",
                    "Usage unit must match the feed type unit.");
        }
        if (request.usageDate().isBefore(repository.batchPlacementDate(batchId))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_USAGE_DATE",
                    "Feed usage cannot be dated before the batch placement date.");
        }

        LocalDate latestUsage = repository.latestUsageDate(type.id());
        if (latestUsage != null && request.usageDate().isBefore(latestUsage)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_USAGE_DATE",
                    "Feed usage cannot be backdated before existing usage for that feed type.");
        }

        long quantityMilli = toMilli(request.quantity());
        FeedUsageEvent proposed = new FeedUsageEvent(
                UUID.randomUUID().toString(), batchId, type.id(), request.usageDate(), quantityMilli, Instant.now()
        );
        List<FeedPurchaseLot> purchases = repository.findPurchases(type.id(), request.usageDate());
        List<FeedUsageEvent> existingUsages = repository.findUsages(type.id(), request.usageDate());
        long feedCost;
        try {
            feedCost = FeedCostCalculator.costForUsage(purchases, existingUsages, proposed);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_FEED", e.getMessage());
        }

        repository.insertUsage(proposed.id(), batchId, type.id(), request.usageDate(),
                quantityMilli, proposed.createdAt().toString());
        if (feedCost > 0) {
            birdCostService.addAdditionalCost(batchId,
                    new BirdAdditionalCostRequest(request.usageDate(), feedCost, "Feed usage: " + type.name()));
        }

        auditRepository.append(farmId, "CREATE", "FEED_USAGE", proposed.id(), null, null,
                String.format("{\"batchId\":\"%s\",\"feedTypeId\":\"%s\",\"quantityMilli\":%d,\"feedCostMinor\":%d}",
                        batchId, type.id(), quantityMilli, feedCost), proposed.createdAt().toString());

        return new FeedUsageResponse(proposed.id(), batchId, type.id(), type.name(), type.unit(),
                request.usageDate(), quantityMilli, feedCost);
    }

    public BatchFeedCostResponse batchCost(String batchId, LocalDate asOf) {
        requireBatch(batchId);
        LocalDate effective = asOf == null ? LocalDate.now() : asOf;
        List<FeedUsageEvent> usages = repository.findBatchUsages(batchId, effective);
        long totalQuantity = 0L;
        long totalCost = 0L;
        List<String> feedTypeIds = usages.stream().map(FeedUsageEvent::feedTypeId).distinct().toList();

        for (String feedTypeId : feedTypeIds) {
            List<FeedPurchaseLot> purchases = repository.findPurchases(feedTypeId, effective);
            List<FeedUsageEvent> allUsages = repository.findUsages(feedTypeId, effective);
            FeedCostCalculator.FifoSnapshot snapshot = FeedCostCalculator.calculate(purchases, allUsages, effective);
            for (FeedUsageEvent usage : usages) {
                if (feedTypeId.equals(usage.feedTypeId())) {
                    totalQuantity = Math.addExact(totalQuantity, usage.quantityMilli());
                    totalCost = Math.addExact(totalCost, snapshot.usageCostsMinor().getOrDefault(usage.id(), 0L));
                }
            }
        }

        int currentBirds = populationService.get(batchId, effective).currentBirds();
        Long perBird = totalQuantity > 0 && currentBirds > 0 ? totalCost / currentBirds : null;
        return new BatchFeedCostResponse(batchId, effective, currentBirds, totalQuantity, totalCost, perBird);
    }

    private FeedTypeResponse requireType(String id) {
        FeedTypeResponse type = repository.findType(id);
        if (type == null) throw new ApiException(HttpStatus.NOT_FOUND, "FEED_TYPE_NOT_FOUND", "Feed type not found.");
        return type;
    }

    private String requireBatch(String batchId) {
        try {
            return repository.batchFarmId(batchId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found.");
        }
    }

    private long toMilli(BigDecimal quantity) {
        try {
            return quantity.setScale(3, RoundingMode.UNNECESSARY).movePointRight(3).longValueExact();
        } catch (ArithmeticException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_QUANTITY",
                    "Feed quantity can have at most 3 decimal places.");
        }
    }

    private boolean isApplicable(String scope, String batchType) {
        return "BOTH".equals(scope) || scope.equals(batchType);
    }

    private String normalizeScope(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
