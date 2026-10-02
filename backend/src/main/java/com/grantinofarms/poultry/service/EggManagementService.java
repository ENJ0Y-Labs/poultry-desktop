package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.domain.EggInventoryCalculator;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.EggManagementRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.FarmSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class EggManagementService {
    private final EggManagementRepository repository;
    private final FarmRepository farmRepository;
    private final FarmSettingsRepository settingsRepository;
    private final AuditRepository auditRepository;

    public EggManagementService(EggManagementRepository repository,
                                FarmRepository farmRepository,
                                FarmSettingsRepository settingsRepository,
                                AuditRepository auditRepository) {
        this.repository = repository;
        this.farmRepository = farmRepository;
        this.settingsRepository = settingsRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public EggCollectionResponse addCollection(String batchId, EggCollectionRequest request) {
        String farmId = requireActiveLayer(batchId);
        validateDate(batchId, request.recordDate());

        int good = request.good();
        int cracked = request.cracked();
        EggInventoryCalculator.totalCollected(good, cracked);

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        String notes = cleanNullable(request.notes());
        repository.insertCollection(id, batchId, request.recordDate(), good, cracked, notes, now);
        auditRepository.append(farmId, "CREATE", "EGG_COLLECTION", id, notes, null,
                String.format("{\"batchId\":\"%s\",\"recordDate\":\"%s\",\"good\":%d,\"cracked\":%d}",
                        batchId, request.recordDate(), good, cracked), now);
        return new EggCollectionResponse(id, batchId, request.recordDate(), good, cracked,
                good + cracked, notes);
    }

    public List<EggCollectionResponse> collections(String batchId, LocalDate asOf) {
        requireLayer(batchId);
        return repository.findCollections(batchId, asOf);
    }

    @Transactional
    public EggSaleResponse addSale(String batchId, EggSaleRequest request) {
        String farmId = requireActiveLayer(batchId);
        validateDate(batchId, request.recordDate());

        int crateSize = crateSize(farmId);
        BigDecimal crates = request.crates().stripTrailingZeros();
        BigDecimal eggsDecimal = crates.multiply(BigDecimal.valueOf(crateSize));
        if (eggsDecimal.scale() > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EGG_QUANTITY",
                    "Crate quantity must convert to a whole number of individual eggs.");
        }
        int soldEggs;
        try {
            soldEggs = eggsDecimal.intValueExact();
        } catch (ArithmeticException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EGG_QUANTITY",
                    "Crate quantity is too large.");
        }

        if (soldEggs <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EGG_QUANTITY",
                    "Egg sale quantity must be greater than zero.");
        }

        long price = request.pricePerCrateMinor();
        BigDecimal totalDecimal = BigDecimal.valueOf(soldEggs)
                .multiply(BigDecimal.valueOf(price))
                .divide(BigDecimal.valueOf(crateSize), 0, RoundingMode.UNNECESSARY);
        long total;
        try {
            total = totalDecimal.longValueExact();
        } catch (ArithmeticException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EGG_PRICE",
                    "This crate price produces a fractional minor-unit sale total.");
        }

        int collected = repository.goodCollectedThrough(batchId, request.recordDate());
        int sold = repository.goodSoldThrough(batchId, request.recordDate());
        int available = EggInventoryCalculator.goodRemaining(collected, sold);
        if (soldEggs > available) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_GOOD_EGGS",
                    "The sale exceeds good eggs available on that date.");
        }

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        String customer = request.customer().trim();
        repository.insertSale(id, batchId, request.recordDate(), customer, soldEggs,
                crateSize, price, total, now);
        auditRepository.append(farmId, "CREATE", "EGG_SALE", id, customer, null,
                String.format("{\"batchId\":\"%s\",\"recordDate\":\"%s\",\"customer\":\"%s\",\"soldEggs\":%d,\"crateSize\":%d,\"totalMinor\":%d}",
                        batchId, request.recordDate(), customer, soldEggs, crateSize, total), now);
        return new EggSaleResponse(id, batchId, request.recordDate(), customer, crates,
                soldEggs, crateSize, price, total);
    }

    public List<EggSaleResponse> sales(String batchId, LocalDate asOf) {
        requireLayer(batchId);
        return repository.findSales(batchId, asOf);
    }

    public EggInventoryResponse inventory(String batchId, LocalDate asOf) {
        requireLayer(batchId);
        int goodCollected = repository.goodCollected(batchId, asOf);
        int cracked = repository.crackedCollected(batchId, asOf);
        int goodSold = repository.goodSold(batchId, asOf);
        int remaining = EggInventoryCalculator.goodRemaining(goodCollected, goodSold);
        return new EggInventoryResponse(batchId, goodCollected, cracked, goodSold,
                remaining, EggInventoryCalculator.totalCollected(goodCollected, cracked));
    }

    private int crateSize(String farmId) {
        FarmSettingsResponse settings = settingsRepository.find(farmId);
        if (settings == null || settings.defaultCrateSize() == null || settings.defaultCrateSize() <= 0) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CRATE_SIZE_MISSING",
                    "The farm's default crate size is not configured.");
        }
        return settings.defaultCrateSize();
    }

    private String requireActiveLayer(String batchId) {
        requireLayer(batchId);
        if (!"ACTIVE".equals(repository.batchStatus(batchId))) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_ACTIVE",
                    "Egg records cannot be added to a non-ACTIVE batch.");
        }
        return repository.farmIdForBatch(batchId);
    }

    private void requireLayer(String batchId) {
        if (repository.farmIdForBatch(batchId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found.");
        }
        if (!"LAYER".equals(repository.batchType(batchId))) {
            throw new ApiException(HttpStatus.CONFLICT, "LAYER_FEATURE_ONLY",
                    "Egg production is only available for LAYER batches.");
        }
    }

    private void validateDate(String batchId, LocalDate date) {
        if (date.isBefore(repository.placementDate(batchId))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EGG_RECORD_DATE",
                    "Egg records cannot be dated before the batch placement date.");
        }
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
