package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.domain.BirdPopulationEvent;
import com.grantinofarms.poultry.domain.PricingCalculator;
import com.grantinofarms.poultry.dto.PricingResponse;
import com.grantinofarms.poultry.dto.PricingSettingsRequest;
import com.grantinofarms.poultry.dto.PricingSettingsResponse;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.BatchRepository;
import com.grantinofarms.poultry.repository.BirdCostRepository;
import com.grantinofarms.poultry.repository.BirdPopulationRepository;
import com.grantinofarms.poultry.repository.PricingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class PricingService {
    private final PricingRepository pricingRepository;
    private final BatchRepository batchRepository;
    private final BirdCostRepository costRepository;
    private final BirdPopulationRepository populationRepository;
    private final AuditRepository auditRepository;

    public PricingService(PricingRepository pricingRepository,
                          BatchRepository batchRepository,
                          BirdCostRepository costRepository,
                          BirdPopulationRepository populationRepository,
                          AuditRepository auditRepository) {
        this.pricingRepository = pricingRepository;
        this.batchRepository = batchRepository;
        this.costRepository = costRepository;
        this.populationRepository = populationRepository;
        this.auditRepository = auditRepository;
    }

    public PricingSettingsResponse settings() {
        return pricingRepository.settings(requireFarmId());
    }

    @Transactional
    public PricingSettingsResponse updateSettings(PricingSettingsRequest request) {
        String farmId = requireFarmId();
        PricingSettingsResponse old = pricingRepository.settings(farmId);
        BigDecimal target = request.targetMarginPercent();
        String now = Instant.now().toString();

        if (target == null ? old.targetMarginPercent() != null
                : !target.equals(old.targetMarginPercent())) {
            pricingRepository.updateTarget(farmId, target, now);
            pricingRepository.insertHistory(
                    UUID.randomUUID().toString(), farmId, now, "TARGET_SET",
                    old.targetMarginPercent(), target,
                    old.workingMarginPercent(), old.workingMarginPercent(),
                    "Pricing target changed", "PRICING_SETTINGS", farmId
            );
            auditRepository.append(
                    farmId, "UPDATE", "PRICING_SETTINGS", farmId,
                    "Pricing target changed", null,
                    String.format("{\"targetMarginPercent\":%s}", target == null ? "null" : target.toPlainString()),
                    now
            );
        }
        return pricingRepository.settings(farmId);
    }

    public PricingResponse price(String batchId, int quantity, LocalDate asOf) {
        requireBroiler(batchId);
        if (quantity <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICING_QUANTITY", "Quantity must be positive.");
        }
        LocalDate effective = asOf == null ? LocalDate.now() : asOf;
        long actualCost = actualCost(batchId, quantity, effective);
        PricingSettingsResponse settings = pricingRepository.settings(batchRepository.findById(batchId).farmId());

        Long targetPrice = settings.targetMarginPercent() == null
                ? null
                : PricingCalculator.priceForMargin(actualCost, settings.targetMarginPercent());
        Long workingPrice = settings.workingMarginPercent() == null
                ? null
                : PricingCalculator.priceForMargin(actualCost, settings.workingMarginPercent());

        return new PricingResponse(batchId, effective, quantity, actualCost,
                settings.targetMarginPercent(), settings.workingMarginPercent(),
                targetPrice, workingPrice);
    }

    public BigDecimal marginForSale(String batchId, int quantity, long salePricePerBirdMinor, LocalDate date) {
        long cost = actualCost(batchId, quantity, date);
        long revenue = Math.multiplyExact(quantity, salePricePerBirdMinor);
        return PricingCalculator.marginPercent(revenue, cost);
    }

    @Transactional
    public void recordSaleMargin(String batchId, int quantity, long salePricePerBirdMinor,
                                 LocalDate date, String saleId, boolean confirmedBelowTarget) {
        String farmId = batchRepository.findById(batchId).farmId();
        PricingSettingsResponse settings = pricingRepository.settings(farmId);
        BigDecimal actualMargin = marginForSale(batchId, quantity, salePricePerBirdMinor, date);

        if (PricingCalculator.isBelowTarget(actualMargin, settings.targetMarginPercent())
                && !confirmedBelowTarget) {
            throw new ApiException(HttpStatus.CONFLICT, "BELOW_TARGET_CONFIRMATION_REQUIRED",
                    "This sale is below the configured target margin. Confirmation is required.");
        }

        BigDecimal newWorking = PricingCalculator.raiseWorkingMargin(
                settings.workingMarginPercent(), actualMargin);

        if (!java.util.Objects.equals(settings.workingMarginPercent(), newWorking)) {
            String now = Instant.now().toString();
            pricingRepository.updateWorking(farmId, newWorking, now);
            pricingRepository.insertHistory(
                    UUID.randomUUID().toString(), farmId, now, "WORKING_UPDATED",
                    settings.targetMarginPercent(), settings.targetMarginPercent(),
                    settings.workingMarginPercent(), newWorking,
                    "Working margin increased from actual sale margin",
                    "BIRD_SALE", saleId
            );
            auditRepository.append(
                    farmId, "UPDATE", "PRICING_MARGIN", saleId,
                    "Working margin updated from actual sale", null,
                    String.format("{\"oldWorkingMarginPercent\":%s,\"newWorkingMarginPercent\":%s,\"actualMarginPercent\":%s}",
                            settings.workingMarginPercent(), newWorking, actualMargin),
                    now
            );
        }

        if (confirmedBelowTarget) {
            String now = Instant.now().toString();
            auditRepository.append(
                    farmId, "CONFIRM", "PRICING_MARGIN", saleId,
                    "Below-target sale confirmed", null,
                    String.format("{\"targetMarginPercent\":%s,\"actualMarginPercent\":%s}",
                            settings.targetMarginPercent(), actualMargin),
                    now
            );
        }
    }

    private long actualCost(String batchId, int quantity, LocalDate date) {
        if (batchRepository.findById(batchId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found.");
        }
        var batch = batchRepository.findById(batchId);
        try {
            BirdPopulationEvent proposed = new BirdPopulationEvent(
                    date, "SOLD", quantity, null, "Pricing preview", null
            );
            return com.grantinofarms.poultry.domain.BirdCostCalculator.costForReduction(
                    populationRepository.initialBirds(batchId),
                    costRepository.initialCost(batchId),
                    populationRepository.findEvents(batchId),
                    costRepository.findEvents(batchId),
                    proposed
            );
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_PRICING_COST", e.getMessage());
        }
    }

    private void requireBroiler(String batchId) {
        var batch = batchRepository.findById(batchId);
        if (batch == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found.");
        }
        if (!"BROILER".equals(batch.type())) {
            throw new ApiException(HttpStatus.CONFLICT, "BROILER_ONLY", "Pricing is currently available for BROILER batches.");
        }
    }

    private String requireFarmId() {
        return batchRepository.findAnyFarmId();
    }
}
