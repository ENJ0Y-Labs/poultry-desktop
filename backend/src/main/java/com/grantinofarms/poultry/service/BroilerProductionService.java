package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.domain.FcrCalculator;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.BroilerProductionRepository;
import com.grantinofarms.poultry.repository.SalesRepository;
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
public class BroilerProductionService {
    private final BroilerProductionRepository repository;
    private final BirdPopulationService populationService;
    private final BatchService batchService;
    private final AuditRepository auditRepository;
    private final PricingService pricingService;
    private final CustomerService customerService;
    private final SalesRepository salesRepository;

    public BroilerProductionService(BroilerProductionRepository repository,
                                    BirdPopulationService populationService,
                                    BatchService batchService,
                                    AuditRepository auditRepository,
                                    PricingService pricingService,
                                    CustomerService customerService,
                                    SalesRepository salesRepository) {
        this.repository = repository;
        this.populationService = populationService;
        this.batchService = batchService;
        this.auditRepository = auditRepository;
        this.pricingService = pricingService;
        this.customerService = customerService;
        this.salesRepository = salesRepository;
    }

    @Transactional
    public WeightRecordResponse addWeight(String batchId, WeightRecordRequest request) {
        requireActiveMeatBirdBatch(batchId);
        validateDate(batchId, request.recordDate());

        long grams;
        try {
            grams = request.totalWeightKg()
                    .setScale(3, RoundingMode.UNNECESSARY)
                    .movePointRight(3)
                    .longValueExact();
        } catch (ArithmeticException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEIGHT",
                    "Total weight can have at most 3 decimal places in kilograms.");
        }

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        repository.insertWeight(id, batchId, request.recordDate(), request.sampleQuantity(),
                grams, clean(request.notes()), now);

        auditRepository.append(repository.batchFarmId(batchId), "CREATE", "WEIGHT_RECORD",
                id, null, null,
                String.format("{\"batchId\":\"%s\",\"recordDate\":\"%s\",\"sampleQuantity\":%d,\"totalWeightKg\":%s}",
                        batchId, request.recordDate(), request.sampleQuantity(),
                        request.totalWeightKg().stripTrailingZeros().toPlainString()),
                now);

        List<WeightRecordResponse> records = growthTrend(batchId, request.recordDate());
        return records.get(records.size() - 1);
    }

    /** Undo is intentionally restricted to the latest effective measurement. */
    @Transactional
    public java.util.Map<String, Object> undoWeight(String batchId, String weightId, String suppliedReason) {
        String actorId = CurrentUserContext.get();
        if (actorId == null || actorId.isBlank()) throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Sign in as the farm owner before undoing a record.");
        String reason = suppliedReason == null ? "" : suppliedReason.trim();
        if (reason.isBlank() || reason.length() > 500) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVERSAL_REASON", "Enter a reason of 1 to 500 characters.");
        requireBatch(batchId);
        java.util.Map<String,Object> original = repository.findWeightForUndo(batchId, weightId);
        if (original == null) throw new ApiException(HttpStatus.NOT_FOUND, "WEIGHT_RECORD_NOT_FOUND", "The weight record could not be found in this batch.");
        if (repository.isWeightReversed(weightId)) throw new ApiException(HttpStatus.CONFLICT, "TRANSACTION_ALREADY_REVERSED", "This weight record has already been undone.");
        String date = String.valueOf(original.get("record_date"));
        String createdAt = String.valueOf(original.get("created_at"));
        if (repository.hasLaterEffectiveWeight(batchId, date, createdAt)) throw new ApiException(HttpStatus.CONFLICT, "WEIGHT_RECORD_HAS_DEPENDENTS", "This is not the latest weight record. Undo later weight records first.");
        String farmId = repository.batchFarmId(batchId);
        String now = Instant.now().toString();
        String before = String.format("{\"batchId\":\"%s\",\"recordDate\":\"%s\",\"sampleQuantity\":%s,\"totalWeightGrams\":%s}", batchId, date, original.get("sample_quantity"), original.get("total_weight_grams"));
        try {
            repository.insertWeightReversal(UUID.randomUUID().toString(), farmId, batchId, weightId, reason, actorId, now);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSACTION_ALREADY_REVERSED",
                    "This weight record has already been undone.");
        }
        auditRepository.append(farmId, "VOID", "WEIGHT_RECORD", weightId, reason, before, "{\"status\":\"VOIDED\"}", now);
        return java.util.Map.of("originalId", weightId, "status", "VOIDED", "reason", reason, "occurredAt", now);
    }

    public List<WeightRecordResponse> weights(String batchId, LocalDate asOf) {
        requireBatch(batchId);
        LocalDate effective = asOf == null ? LocalDate.now() : asOf;
        return growthTrend(batchId, effective);
    }

    public BroilerGrowthResponse growth(String batchId, LocalDate asOf) {
        requireBatch(batchId);
        LocalDate effective = asOf == null ? LocalDate.now() : asOf;
        List<WeightRecordResponse> trend = growthTrend(batchId, effective);
        if (trend.isEmpty()) {
            return new BroilerGrowthResponse(batchId, effective, null, null, null, null, trend);
        }

        WeightRecordResponse first = trend.get(0);
        WeightRecordResponse latest = trend.get(trend.size() - 1);
        BigDecimal currentAverage = latest.averageWeightKg();

        int currentBirds = populationService.get(batchId, effective).currentBirds();
        BigDecimal currentBiomass = currentAverage.multiply(BigDecimal.valueOf(currentBirds));
        BigDecimal startingBiomass = first.averageWeightKg()
                .multiply(BigDecimal.valueOf(repository.initialBirds(batchId)));
        BigDecimal liveWeightGain = currentBiomass.subtract(startingBiomass);

        BigDecimal feedKg = repository.feedConsumedKg(batchId, effective);
        BigDecimal fcr = liveWeightGain.signum() > 0
                ? FcrCalculator.calculate(feedKg, liveWeightGain)
                : null;

        return new BroilerGrowthResponse(batchId, effective, currentAverage,
                liveWeightGain, feedKg, fcr, trend);
    }

    @Transactional
    public BirdSaleResponse addSale(String batchId, BirdSaleRequest request) {
        requireActiveMeatBirdBatch(batchId);
        validateDate(batchId, request.recordDate());

        long total;
        try {
            total = Math.multiplyExact(request.quantity(), request.pricePerBirdMinor());
        } catch (ArithmeticException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SALE_TOTAL_OVERFLOW",
                    "The bird sale total is too large.");
        }

        String id = UUID.randomUUID().toString();
        var customerRecord = customerService.requireForSale(request.customerId(), request.customer());
        String customer = customerRecord.name();

        pricingService.recordSaleMargin(
                batchId,
                request.quantity(),
                request.pricePerBirdMinor(),
                request.recordDate(),
                id,
                Boolean.TRUE.equals(request.confirmedBelowTarget())
        );

        populationService.addSale(batchId, new BirdPopulationEventRequest(
                request.recordDate(), request.quantity(), "Bird sale: " + customer
        ));

        String now = Instant.now().toString();
        repository.insertSale(id, batchId, request.recordDate(), request.quantity(),
                request.pricePerBirdMinor(), total, customer, customerRecord.id(), now);
        salesRepository.insert(
                id, repository.batchFarmId(batchId), batchId, customerRecord.id(),
                request.recordDate(), repository.batchType(batchId),
                BigDecimal.valueOf(request.quantity()), "BIRD",
                request.pricePerBirdMinor(), total, "BIRD_SALE", id, now);

        auditRepository.append(repository.batchFarmId(batchId), "CREATE", "BIRD_SALE",
                id, null, null,
                String.format("{\"batchId\":\"%s\",\"recordDate\":\"%s\",\"quantity\":%d,\"pricePerBirdMinor\":%d,\"totalAmountMinor\":%d,\"customer\":\"%s\"}",
                        batchId, request.recordDate(), request.quantity(),
                        request.pricePerBirdMinor(), total, customer),
                now);

        BirdSaleResponse response = new BirdSaleResponse(
                id, batchId, request.recordDate(), request.quantity(),
                request.pricePerBirdMinor(), total, clean(request.customer())
        );

        if (!request.recordDate().isAfter(LocalDate.now())
                && populationService.get(batchId, LocalDate.now()).currentBirds() == 0) {
            batchService.markSold(batchId);
        }

        return response;
    }

    public List<BirdSaleResponse> sales(String batchId, LocalDate asOf) {
        requireBatch(batchId);
        LocalDate effective = asOf == null ? LocalDate.now() : asOf;
        return repository.findSales(batchId, effective);
    }

    private List<WeightRecordResponse> growthTrend(String batchId, LocalDate asOf) {
        List<WeightRecordResponse> raw = repository.findWeights(batchId, asOf);
        if (raw.isEmpty()) return raw;

        BigDecimal previous = raw.get(0).averageWeightKg();
        for (int i = 0; i < raw.size(); i++) {
            WeightRecordResponse current = raw.get(i);
            BigDecimal gain = current.averageWeightKg().subtract(previous);
            raw.set(i, new WeightRecordResponse(
                    current.id(), current.batchId(), current.recordDate(),
                    current.sampleQuantity(), current.totalWeightKg(),
                    current.averageWeightKg(),
                    i == 0 ? BigDecimal.ZERO.setScale(6) : gain,
                    current.notes()
            ));
            previous = current.averageWeightKg();
        }
        return raw;
    }

    private void requireActiveMeatBirdBatch(String batchId) {
        requireBatch(batchId);
        if (!isMeatBirdType(repository.batchType(batchId))) {
            throw new ApiException(HttpStatus.CONFLICT, "MEAT_PRODUCTION_ONLY",
                    "Weight and bird sales are only available for BROILER or TURKEY batches.");
        }
        if (!"ACTIVE".equals(repository.batchStatus(batchId))) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_ACTIVE",
                    "Broiler production records cannot be added to a non-ACTIVE batch.");
        }
    }

    private boolean isMeatBirdType(String type) {
        return "BROILER".equals(type) || "TURKEY".equals(type);
    }

    private void requireBatch(String batchId) {
        if (repository.batchFarmId(batchId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found.");
        }
        if (!isMeatBirdType(repository.batchType(batchId))) {
            throw new ApiException(HttpStatus.CONFLICT, "MEAT_PRODUCTION_ONLY",
                    "Weight and bird sales are only available for BROILER or TURKEY batches.");
        }
    }

    private void validateDate(String batchId, LocalDate date) {
        LocalDate placement = repository.placementDate(batchId);
        if (placement != null && date.isBefore(placement)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECORD_DATE",
                    "The record date cannot be before batch placement.");
        }
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}
