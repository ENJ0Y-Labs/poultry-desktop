package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.domain.BirdCostCalculator;
import com.grantinofarms.poultry.domain.BirdCostEvent;
import com.grantinofarms.poultry.domain.BirdPopulationEvent;
import com.grantinofarms.poultry.dto.BirdAdditionalCostRequest;
import com.grantinofarms.poultry.dto.BirdCostResponse;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.BatchRepository;
import com.grantinofarms.poultry.repository.BirdCostRepository;
import com.grantinofarms.poultry.repository.BirdPopulationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BirdCostService {
    private final BirdCostRepository costRepository;
    private final BirdPopulationRepository populationRepository;
    private final BatchRepository batchRepository;
    private final AuditRepository auditRepository;

    public BirdCostService(BirdCostRepository costRepository,
                           BirdPopulationRepository populationRepository,
                           BatchRepository batchRepository,
                           AuditRepository auditRepository) {
        this.costRepository = costRepository;
        this.populationRepository = populationRepository;
        this.batchRepository = batchRepository;
        this.auditRepository = auditRepository;
    }

    public BirdCostResponse get(String batchId, LocalDate asOf) {
        var batch = requireBatch(batchId);
        LocalDate effectiveDate = asOf == null ? LocalDate.now() : asOf;
        List<BirdPopulationEvent> populationEvents = populationRepository.findEvents(batchId);
        List<BirdCostEvent> costEvents = costRepository.findEvents(batchId);

        BirdCostCalculator.CostSnapshot snapshot = BirdCostCalculator.calculate(
                batch.initialBirdCount(),
                costRepository.initialCost(batchId),
                populationEvents,
                costEvents,
                effectiveDate
        );

        long additionalCosts = costEvents.stream()
                .filter(event -> "ADDITIONAL_COST".equals(event.eventType()))
                .filter(event -> !event.eventDate().isAfter(effectiveDate))
                .mapToLong(BirdCostEvent::amountMinor)
                .reduce(0L, Math::addExact);

        long sold = reductionCost(batchId, effectiveDate, populationEvents, costEvents, "SOLD");
        long mortality = reductionCost(batchId, effectiveDate, populationEvents, costEvents, "MORTALITY");
        long culling = reductionCost(batchId, effectiveDate, populationEvents, costEvents, "CULLING");
        long transferOut = reductionCost(batchId, effectiveDate, populationEvents, costEvents, "TRANSFER_OUT");

        return new BirdCostResponse(
                batchId,
                effectiveDate,
                snapshot.currentBirds(),
                costRepository.initialCost(batchId),
                additionalCosts,
                sold,
                mortality,
                culling,
                transferOut,
                snapshot.carriedCostMinor()
        );
    }

    @Transactional
    public BirdCostResponse addAdditionalCost(String batchId, BirdAdditionalCostRequest request) {
        var batch = requireWritableBatch(batchId);
        validateBusinessDate(batch, request.eventDate());

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        costRepository.insert(
                id,
                batchId,
                request.eventDate(),
                "ADDITIONAL_COST",
                request.amountMinor(),
                null,
                clean(request.reason()),
                now
        );

        auditRepository.append(
                batch.farmId(),
                "CREATE",
                "BIRD_COST_EVENT",
                id,
                clean(request.reason()),
                null,
                String.format(
                        "{\"batchId\":\"%s\",\"eventType\":\"ADDITIONAL_COST\",\"amountMinor\":%d,\"eventDate\":\"%s\"}",
                        batchId,
                        request.amountMinor(),
                        request.eventDate()
                ),
                now
        );

        return get(batchId, request.eventDate());
    }

    public long costForReduction(String batchId, BirdPopulationEvent proposedReduction) {
        requireBatch(batchId);
        try {
            return BirdCostCalculator.costForReduction(
                    populationRepository.initialBirds(batchId),
                    costRepository.initialCost(batchId),
                    populationRepository.findEvents(batchId),
                    costRepository.findEvents(batchId),
                    proposedReduction
            );
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_BIRD_COST",
                    e.getMessage()
            );
        }
    }

    @Transactional
    public void addTransferInCost(
            String targetBatchId,
            LocalDate eventDate,
            long amountMinor,
            String sourceBatchId,
            String reason
    ) {
        var batch = requireWritableBatch(targetBatchId);
        validateBusinessDate(batch, eventDate);

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        costRepository.insert(
                id,
                targetBatchId,
                eventDate,
                "TRANSFER_IN_COST",
                amountMinor,
                sourceBatchId,
                clean(reason),
                now
        );
    }

    private long reductionCost(
            String batchId,
            LocalDate asOf,
            List<BirdPopulationEvent> allEvents,
            List<BirdCostEvent> costEvents,
            String eventType
    ) {
        List<BirdPopulationEvent> prefix = new ArrayList<>();

        long total = 0L;
        for (BirdPopulationEvent event : allEvents) {
            if (event.eventDate().isAfter(asOf)) {
                break;
            }
            if (event.eventType().equals(eventType)) {
                total = Math.addExact(
                        total,
                        BirdCostCalculator.costForReduction(
                                populationRepository.initialBirds(batchId),
                                costRepository.initialCost(batchId),
                                prefix,
                                costEvents,
                                event
                        )
                );
            }
            prefix.add(event);
        }
        return total;
    }

    private com.grantinofarms.poultry.dto.BatchResponse requireBatch(String batchId) {
        var batch = batchRepository.findById(batchId);
        if (batch == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found.");
        }
        return batch;
    }

    private com.grantinofarms.poultry.dto.BatchResponse requireWritableBatch(String batchId) {
        var batch = requireBatch(batchId);
        if (!"ACTIVE".equals(batch.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_ACTIVE",
                    "Bird costs cannot be changed on a non-ACTIVE batch.");
        }
        return batch;
    }

    private void validateBusinessDate(com.grantinofarms.poultry.dto.BatchResponse batch, LocalDate eventDate) {
        if (eventDate.isBefore(batch.placementDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COST_DATE",
                    "A bird cost date cannot be before the batch placement date.");
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
