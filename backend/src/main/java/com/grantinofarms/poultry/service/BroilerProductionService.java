package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.domain.FcrCalculator;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.BroilerProductionRepository;
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

    public BroilerProductionService(BroilerProductionRepository repository,
                                    BirdPopulationService populationService,
                                    BatchService batchService,
                                    AuditRepository auditRepository,
                                    PricingService pricingService) {
        this.repository = repository;
        this.populationService = populationService;
        this.batchService = batchService;
        this.auditRepository = auditRepository;
        this.pricingService = pricingService;
    }

    @Transactional
    public WeightRecordResponse addWeight(String batchId, WeightRecordRequest request) {
        requireActiveBroiler(batchId);
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
            return new BroilerGrowthResponse(batchId, effective, null, null, null, trend);
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
                liveWeightGain, fcr, trend);
    }

    @Transactional
    public BirdSaleResponse addSale(String batchId, BirdSaleRequest request) {
        requireActiveBroiler(batchId);
        validateDate(batchId, request.recordDate());

        long total;
        try {
            total = Math.multiplyExact(request.quantity(), request.pricePerBirdMinor());
        } catch (ArithmeticException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SALE_TOTAL_OVERFLOW",
                    "The bird sale total is too large.");
        }

        String id = UUID.randomUUID().toString();

        pricingService.recordSaleMargin(
                batchId,
                request.quantity(),
                request.pricePerBirdMinor(),
                request.recordDate(),
                id,
                Boolean.TRUE.equals(request.confirmedBelowTarget())
        );

        populationService.addSale(batchId, new BirdPopulationEventRequest(
                request.recordDate(), request.quantity(), "Bird sale: " + clean(request.customer())
        ));

        String now = Instant.now().toString();
        repository.insertSale(id, batchId, request.recordDate(), request.quantity(),
                request.pricePerBirdMinor(), total, clean(request.customer()), now);

        auditRepository.append(repository.batchFarmId(batchId), "CREATE", "BIRD_SALE",
                id, null, null,
                String.format("{\"batchId\":\"%s\",\"recordDate\":\"%s\",\"quantity\":%d,\"pricePerBirdMinor\":%d,\"totalAmountMinor\":%d,\"customer\":\"%s\"}",
                        batchId, request.recordDate(), request.quantity(),
                        request.pricePerBirdMinor(), total, clean(request.customer())),
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

    private void requireActiveBroiler(String batchId) {
        requireBatch(batchId);
        if (!"BROILER".equals(repository.batchType(batchId))) {
            throw new ApiException(HttpStatus.CONFLICT, "BROILER_ONLY",
                    "This feature is only available for BROILER batches.");
        }
        if (!"ACTIVE".equals(repository.batchStatus(batchId))) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_ACTIVE",
                    "Broiler production records cannot be added to a non-ACTIVE batch.");
        }
    }

    private void requireBatch(String batchId) {
        if (repository.batchFarmId(batchId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found.");
        }
        if (!"BROILER".equals(repository.batchType(batchId))) {
            throw new ApiException(HttpStatus.CONFLICT, "BROILER_ONLY",
                    "This feature is only available for BROILER batches.");
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
