package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.domain.BirdPopulationCalculator;
import com.grantinofarms.poultry.domain.BirdPopulationEvent;
import com.grantinofarms.poultry.dto.BirdPopulationEventRequest;
import com.grantinofarms.poultry.dto.BirdPopulationResponse;
import com.grantinofarms.poultry.dto.BirdTransferRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.BirdPopulationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class BirdPopulationService {
    private final BirdPopulationRepository populationRepository;
    private final AuditRepository auditRepository;

    public BirdPopulationService(BirdPopulationRepository populationRepository,
                                  AuditRepository auditRepository) {
        this.populationRepository = populationRepository;
        this.auditRepository = auditRepository;
    }

    public BirdPopulationResponse get(String batchId, LocalDate asOf) {
        requireBatch(batchId);
        LocalDate effectiveDate = asOf == null ? LocalDate.now() : asOf;
        return calculate(batchId, effectiveDate);
    }

    @Transactional
    public BirdPopulationResponse addMortality(String batchId, BirdPopulationEventRequest request) {
        return addReduction(batchId, request, "MORTALITY");
    }

    @Transactional
    public BirdPopulationResponse addCulling(String batchId, BirdPopulationEventRequest request) {
        return addReduction(batchId, request, "CULLING");
    }

    @Transactional
    public BirdPopulationResponse addSale(String batchId, BirdPopulationEventRequest request) {
        return addReduction(batchId, request, "SOLD");
    }

    @Transactional
    public BirdPopulationResponse transfer(String sourceBatchId, BirdTransferRequest request) {
        requireWritableBatch(sourceBatchId);
        requireWritableBatch(request.targetBatchId());

        String sourceFarmId = populationRepository.batchFarmId(sourceBatchId);
        String targetFarmId = populationRepository.batchFarmId(request.targetBatchId());
        if (!sourceFarmId.equals(targetFarmId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER",
                    "Birds can only be transferred between batches on the same farm.");
        }

        if (sourceBatchId.equals(request.targetBatchId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER",
                    "Birds cannot be transferred to the same batch.");
        }

        List<BirdPopulationEvent> sourceEvents = populationRepository.findEvents(sourceBatchId);
        int sourceInitial = populationRepository.initialBirds(sourceBatchId);

        BirdPopulationEvent outbound = new BirdPopulationEvent(
                request.eventDate(), "TRANSFER_OUT", request.quantity()
        );

        try {
            BirdPopulationCalculator.validatePopulationChange(
                    sourceInitial, sourceEvents, outbound
            );
        } catch (IllegalArgumentException e) {
            throw populationConflict(e.getMessage());
        }

        String now = Instant.now().toString();
        String transferId = UUID.randomUUID().toString();

        populationRepository.insert(
                transferId,
                sourceBatchId,
                request.eventDate(),
                "TRANSFER_OUT",
                request.quantity(),
                request.targetBatchId(),
                clean(request.reason()),
                now
        );

        populationRepository.insert(
                UUID.randomUUID().toString(),
                request.targetBatchId(),
                request.eventDate(),
                "TRANSFER_IN",
                request.quantity(),
                sourceBatchId,
                clean(request.reason()),
                now
        );

        auditRepository.append(
                sourceFarmId,
                "TRANSFER",
                "BIRD_POPULATION",
                transferId,
                clean(request.reason()),
                null,
                String.format(
                        "{\"sourceBatchId\":\"%s\",\"targetBatchId\":\"%s\",\"quantity\":%d}",
                        sourceBatchId,
                        request.targetBatchId(),
                        request.quantity()
                ),
                now
        );

        return calculate(sourceBatchId, request.eventDate());
    }

    private BirdPopulationResponse addReduction(
            String batchId,
            BirdPopulationEventRequest request,
            String eventType
    ) {
        requireWritableBatch(batchId);

        List<BirdPopulationEvent> events = populationRepository.findEvents(batchId);
        int initialBirds = populationRepository.initialBirds(batchId);
        BirdPopulationEvent proposed = new BirdPopulationEvent(
                request.eventDate(), eventType, request.quantity()
        );

        try {
            BirdPopulationCalculator.validatePopulationChange(
                    initialBirds, events, proposed
            );
        } catch (IllegalArgumentException e) {
            throw populationConflict(e.getMessage());
        }

        String now = Instant.now().toString();
        String id = UUID.randomUUID().toString();

        populationRepository.insert(
                id,
                batchId,
                request.eventDate(),
                eventType,
                request.quantity(),
                null,
                clean(request.reason()),
                now
        );

        auditRepository.append(
                populationRepository.batchFarmId(batchId),
                "CREATE",
                "BIRD_POPULATION_EVENT",
                id,
                clean(request.reason()),
                null,
                String.format(
                        "{\"batchId\":\"%s\",\"eventType\":\"%s\",\"quantity\":%d,\"eventDate\":\"%s\"}",
                        batchId,
                        eventType,
                        request.quantity(),
                        request.eventDate()
                ),
                now
        );

        return calculate(batchId, request.eventDate());
    }

    private BirdPopulationResponse calculate(String batchId, LocalDate asOf) {
        int initialBirds = populationRepository.initialBirds(batchId);
        List<BirdPopulationEvent> events = populationRepository.findEvents(batchId);

        int mortality = sum(events, "MORTALITY", asOf);
        int culling = sum(events, "CULLING", asOf);
        int sold = sum(events, "SOLD", asOf);
        int transfersIn = sum(events, "TRANSFER_IN", asOf);
        int transfersOut = sum(events, "TRANSFER_OUT", asOf);
        int current = BirdPopulationCalculator.currentBirds(initialBirds, events, asOf);

        return new BirdPopulationResponse(
                batchId, asOf, initialBirds, mortality, culling, sold,
                transfersIn, transfersOut, current
        );
    }

    private int sum(List<BirdPopulationEvent> events, String type, LocalDate asOf) {
        return events.stream()
                .filter(event -> type.equals(event.eventType()))
                .filter(event -> !event.eventDate().isAfter(asOf))
                .mapToInt(BirdPopulationEvent::quantity)
                .sum();
    }

    private void requireBatch(String batchId) {
        if (!populationRepository.batchExists(batchId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found.");
        }
    }

    private void requireWritableBatch(String batchId) {
        requireBatch(batchId);
        if (!"ACTIVE".equals(populationRepository.batchStatus(batchId))) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_ACTIVE",
                    "Population records cannot be added to a non-ACTIVE batch.");
        }
    }

    private ApiException populationConflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_BIRD_POPULATION", message);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
