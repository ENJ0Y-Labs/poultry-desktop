package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.domain.FeedCostCalculator;
import com.grantinofarms.poultry.domain.FeedPurchaseLot;
import com.grantinofarms.poultry.domain.FeedOpeningStockLot;
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
        List<String> applicableTypes = normalizeApplicableTypes(request);
        String scope = legacyScope(applicableTypes);
        String unit = clean(request.unit());
        String name = clean(request.name());
        String brand = cleanNullable(request.brand());
        BigDecimal kgPerUnit = request.kgPerUnit();
        if (request.packageWeightKg() != null && request.packageWeightKg().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PACKAGE_WEIGHT", "Package weight must be greater than zero, or left blank when unknown.");
        }
        if (kgPerUnit != null && kgPerUnit.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_CONVERSION",
                    "Kilograms per unit must be greater than zero.");
        }
        if ("kg".equalsIgnoreCase(unit)) {
            if (kgPerUnit != null && kgPerUnit.compareTo(BigDecimal.ONE) != 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_CONVERSION",
                        "A kilogram unit must use a conversion factor of exactly 1.");
            }
            kgPerUnit = BigDecimal.ONE;
        }
        String farmId = repository.farmId();
        if (repository.productIdentityExists(farmId, name, brand, null)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_FEED_PRODUCT",
                    "A feed product with this name and brand already exists.");
        }
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        try {
            repository.insertType(id, farmId, name, unit, scope, kgPerUnit, brand, cleanNullable(request.packagingName()), request.packageWeightKg(), applicableTypes, now);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_FEED_PRODUCT",
                    "A feed product with this name and brand already exists.");
        }
        auditRepository.append(farmId, "CREATE", "FEED_TYPE", id, null, null,
                "{\"name\":" + jsonString(name) + ",\"brand\":" + jsonString(brand)
                        + ",\"packagingName\":" + jsonString(cleanNullable(request.packagingName()))
                        + ",\"packageWeightKg\":" + (request.packageWeightKg() == null ? "null" : request.packageWeightKg().toPlainString())
                        + ",\"unit\":" + jsonString(unit) + ",\"applicableTypes\":"
                        + applicableTypesJson(applicableTypes) + ",\"kgPerUnit\":"
                        + (kgPerUnit == null ? "null" : kgPerUnit.toPlainString()) + "}", now);
        return repository.findType(id);
    }

    @Transactional
    public FeedTypeResponse updateProductIdentity(String id, FeedTypeUpdateRequest request) {
        FeedTypeResponse type = requireType(id);
        String name = clean(request.name());
        String brand = cleanNullable(request.brand());
        String packagingName = cleanNullable(request.packagingName());
        BigDecimal packageWeightKg = request.packageWeightKg();
        if (packageWeightKg != null && packageWeightKg.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PACKAGE_WEIGHT",
                    "Package weight must be greater than zero, or left blank when unknown.");
        }
        String farmId = repository.farmId();
        if (repository.productIdentityExists(farmId, name, brand, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_FEED_PRODUCT",
                    "A feed product with this name and brand already exists.");
        }
        String now = Instant.now().toString();
        repository.updateProductIdentity(id, name, brand, packagingName, packageWeightKg, now);
        auditRepository.append(farmId, "UPDATE", "FEED_TYPE", id, "Update feed product identity",
                feedIdentityJson(type.name(), type.brand(), type.packagingName(), type.packageWeightKg()),
                feedIdentityJson(name, brand, packagingName, packageWeightKg), now);
        return repository.findType(id);
    }

    @Transactional
    public FeedTypeResponse updateConversion(String id, FeedTypeConversionRequest request) {
        FeedTypeResponse type = requireType(id);
        BigDecimal kgPerUnit = request.kgPerUnit();
        if (kgPerUnit != null && kgPerUnit.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_CONVERSION",
                    "Kilograms per unit must be greater than zero, or left blank when unknown.");
        }
        if ("kg".equalsIgnoreCase(type.unit())) {
            if (kgPerUnit == null || kgPerUnit.compareTo(BigDecimal.ONE) != 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_CONVERSION",
                        "A kilogram unit must use a conversion factor of exactly 1.");
            }
            kgPerUnit = BigDecimal.ONE;
        }
        String now = Instant.now().toString();
        repository.updateKgPerUnit(id, kgPerUnit, now);
        auditRepository.append(repository.farmId(), "UPDATE", "FEED_TYPE", id,
                "Update feed kilogram conversion",
                "{\"kgPerUnit\":" + (type.kgPerUnit() == null ? "null" : type.kgPerUnit().toPlainString()) + "}",
                "{\"kgPerUnit\":" + (kgPerUnit == null ? "null" : kgPerUnit.toPlainString()) + "}", now);
        return repository.findType(id);
    }

    @Transactional
    public FeedTypeResponse archiveType(String id) {
        FeedTypeResponse type = requireType(id);
        String now = Instant.now().toString();
        repository.archiveType(id, now);
        auditRepository.append(repository.farmId(), "ARCHIVE", "FEED_TYPE", id,
                "Feed type archived",
                String.format("{\"name\":\"%s\",\"unit\":\"%s\",\"applicableTypes\":%s,\"status\":\"ACTIVE\"}", type.name(), type.unit(), applicableTypesJson(type.applicableTypes())),
                String.format("{\"name\":\"%s\",\"unit\":\"%s\",\"applicableTypes\":%s,\"status\":\"ARCHIVED\"}", type.name(), type.unit(), applicableTypesJson(type.applicableTypes())), now);
        return new FeedTypeResponse(type.id(), type.name(), type.unit(), type.applicableType(), "ARCHIVED", type.kgPerUnit(), type.brand(), type.packagingName(), type.packageWeightKg(), type.applicableTypes());
    }

    @Transactional
    public FeedOpeningBalanceResponse createOpeningBalance(FeedOpeningBalanceRequest request) {
        FeedTypeResponse type = requireType(request.feedTypeId());
        if (!"ACTIVE".equals(type.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "FEED_TYPE_NOT_ACTIVE", "The feed type is archived.");
        }
        String farmId = repository.farmId();
        if (!farmId.equals(repository.typeFarmId(type.id()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_TYPE", "Feed type does not belong to the active farm.");
        }
        if (!type.unit().equalsIgnoreCase(clean(request.unit()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_UNIT", "Opening balance unit must match the feed type unit.");
        }
        LocalDate latestUsage = repository.latestUsageDate(type.id());
        if (latestUsage != null && request.effectiveDate().isBefore(latestUsage)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_OPENING_DATE",
                    "Opening stock cannot be backdated before existing feed usage for this feed type.");
        }
        LocalDate latestOpening = repository.latestOpeningBalanceDate(type.id());
        if (latestOpening != null && request.effectiveDate().isBefore(latestOpening)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_OPENING_DATE",
                    "Opening stock cannot be backdated before the latest opening-stock entry for this feed type.");
        }

        long quantityMilli = toMilli(request.quantity());
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        String reason = clean(request.reason());
        repository.insertOpeningBalance(id, farmId, type.id(), request.effectiveDate(),
                quantityMilli, type.unit(), reason, now);
        auditRepository.append(farmId, "CREATE", "FEED_OPENING_BALANCE", id, reason,
                null, String.format("{\"feedTypeId\":\"%s\",\"effectiveDate\":\"%s\",\"quantityMilli\":%d,\"unit\":\"%s\"}",
                        type.id(), request.effectiveDate(), quantityMilli, type.unit()), now);
        return new FeedOpeningBalanceResponse(id, type.id(), type.name(), request.effectiveDate(),
                quantityMilli, type.unit(), reason, now, type.brand());
    }

    public List<FeedOpeningBalanceResponse> openingBalances(String feedTypeId) {
        requireType(feedTypeId);
        return repository.findOpeningBalances(feedTypeId);
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
                quantityMilli, request.totalCostMinor(), type.brand());
    }

    public List<FeedInventoryResponse> inventory(LocalDate asOf) {
        LocalDate effective = asOf == null ? LocalDate.now() : asOf;
        List<FeedInventoryResponse> result = new ArrayList<>();
        for (FeedTypeResponse type : types()) {
            List<FeedPurchaseLot> purchases = repository.findPurchases(type.id(), effective);
            List<FeedOpeningStockLot> openingLots = repository.findOpeningLots(type.id(), effective);
            List<FeedUsageEvent> usages = repository.findUsages(type.id(), effective);
            FeedCostCalculator.FifoSnapshot snapshot = FeedCostCalculator.calculate(purchases, openingLots, usages, effective);
            long purchased = purchases.stream().mapToLong(FeedPurchaseLot::quantityMilli).reduce(0L, Math::addExact);
            long opening = openingLots.stream().mapToLong(FeedOpeningStockLot::quantityMilli).reduce(0L, Math::addExact);
            result.add(new FeedInventoryResponse(type.id(), type.name(), type.unit(), purchased, opening,
                    snapshot.consumedQuantityMilli(), snapshot.remainingQuantityMilli(),
                    snapshot.remainingCostMinor(), snapshot.consumedCostMinor(), type.applicableTypes(), type.brand(), type.packagingName(), type.packageWeightKg()));
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
        if (!isApplicable(type.applicableTypes(), batchType)) {
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
        List<FeedOpeningStockLot> openingLots = repository.findOpeningLots(type.id(), request.usageDate());
        List<FeedUsageEvent> existingUsages = repository.findUsages(type.id(), request.usageDate());
        FeedCostCalculator.UsageCost usageCost;
        try {
            usageCost = FeedCostCalculator.costBreakdownForUsage(purchases, openingLots, existingUsages, proposed);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_FEED", e.getMessage());
        }
        Long feedCost = usageCost.totalCostMinor();
        long knownFeedCost = usageCost.knownCostMinor();

        String reason = request.reason() == null || request.reason().isBlank()
                ? "Production feed usage"
                : request.reason().trim();

        repository.insertUsage(proposed.id(), batchId, type.id(), request.usageDate(),
                quantityMilli, reason, proposed.createdAt().toString());
        if (knownFeedCost > 0) {
            birdCostService.addAdditionalCost(batchId,
                    new BirdAdditionalCostRequest(request.usageDate(), knownFeedCost, "Feed usage: " + type.name()));
        }

        auditRepository.append(farmId, "CREATE", "FEED_USAGE", proposed.id(), null, null,
                String.format("{\"batchId\":\"%s\",\"feedTypeId\":\"%s\",\"quantityMilli\":%d,\"feedCostMinor\":%s,\"knownFeedCostMinor\":%d,\"reason\":\"%s\"}",
                        batchId, type.id(), quantityMilli, feedCost == null ? "null" : feedCost.toString(), knownFeedCost, reason), proposed.createdAt().toString());

        return new FeedUsageResponse(proposed.id(), batchId, type.id(), type.name(), type.unit(),
                request.usageDate(), quantityMilli, feedCost, knownFeedCost);
    }

    public BatchFeedCostResponse batchCost(String batchId, LocalDate asOf) {
        requireBatch(batchId);
        LocalDate effective = asOf == null ? LocalDate.now() : asOf;
        List<FeedUsageEvent> usages = repository.findBatchUsages(batchId, effective);
        long totalQuantity = 0L;
        long knownTotalCost = 0L;
        boolean totalCostUnknown = false;
        List<String> feedTypeIds = usages.stream().map(FeedUsageEvent::feedTypeId).distinct().toList();

        for (String feedTypeId : feedTypeIds) {
            List<FeedPurchaseLot> purchases = repository.findPurchases(feedTypeId, effective);
            List<FeedOpeningStockLot> openingLots = repository.findOpeningLots(feedTypeId, effective);
            List<FeedUsageEvent> allUsages = repository.findUsages(feedTypeId, effective);
            FeedCostCalculator.FifoSnapshot snapshot = FeedCostCalculator.calculate(purchases, openingLots, allUsages, effective);
            for (FeedUsageEvent usage : usages) {
                if (feedTypeId.equals(usage.feedTypeId())) {
                    totalQuantity = Math.addExact(totalQuantity, usage.quantityMilli());
                    if (snapshot.usageCostsMinor().get(usage.id()) == null) totalCostUnknown = true;
                    knownTotalCost = Math.addExact(knownTotalCost,
                            snapshot.usageKnownCostsMinor().getOrDefault(usage.id(), 0L));
                }
            }
        }

        int currentBirds = populationService.get(batchId, effective).currentBirds();
        Long totalCost = totalCostUnknown ? null : knownTotalCost;
        Long perBird = totalQuantity > 0 && currentBirds > 0 && totalCost != null ? totalCost / currentBirds : null;
        return new BatchFeedCostResponse(batchId, effective, currentBirds, totalQuantity,
                totalCost, perBird, knownTotalCost);
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

    private boolean isApplicable(List<String> applicableTypes, String batchType) {
        return applicableTypes.contains(batchType);
    }

    private List<String> normalizeApplicableTypes(FeedTypeCreateRequest request) {
        List<String> requested;
        if (request.applicableTypes() != null) {
            requested = request.applicableTypes().stream()
                    .filter(value -> value != null)
                    .map(this::normalizeScope)
                    .distinct()
                    .toList();
        } else {
            String legacy = normalizeScope(request.applicableType());
            requested = "BOTH".equals(legacy)
                    ? List.of("LAYER", "BROILER", "TURKEY")
                    : List.of(legacy);
        }
        List<String> supported = List.of("LAYER", "BROILER", "TURKEY");
        if (requested.isEmpty() || requested.stream().anyMatch(type -> !supported.contains(type))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEED_SCOPE",
                    "Select at least one supported bird type: LAYER, BROILER, or TURKEY.");
        }
        return supported.stream().filter(requested::contains).toList();
    }

    private String legacyScope(List<String> applicableTypes) {
        return applicableTypes.size() == 3 ? "BOTH"
                : applicableTypes.size() == 1 ? applicableTypes.get(0) : "BOTH";
    }

    private String applicableTypesJson(List<String> applicableTypes) {
        return "[" + applicableTypes.stream().map(type -> "\"" + type + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "]";
    }

    private String normalizeScope(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String feedIdentityJson(String name, String brand, String packagingName, BigDecimal packageWeightKg) {
        return "{\"name\":" + jsonString(name) + ",\"brand\":" + jsonString(brand)
                + ",\"packagingName\":" + jsonString(packagingName)
                + ",\"packageWeightKg\":" + (packageWeightKg == null ? "null" : packageWeightKg.toPlainString()) + "}";
    }

    private String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
