package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.dto.BatchCreateRequest;
import com.grantinofarms.poultry.dto.BatchResponse;
import com.grantinofarms.poultry.dto.BatchReopenRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.BatchRepository;
import com.grantinofarms.poultry.repository.BirdPurchaseRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class BatchService {
    private final FarmRepository farmRepository;
    private final BatchRepository batchRepository;
    private final BirdPurchaseRepository birdPurchaseRepository;
    private final AuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final BirdPopulationService birdPopulationService;

    public BatchService(FarmRepository farmRepository,
                        BatchRepository batchRepository,
                        BirdPurchaseRepository birdPurchaseRepository,
                        AuditRepository auditRepository,
                        ObjectMapper objectMapper,
                        BirdPopulationService birdPopulationService) {
        this.farmRepository = farmRepository;
        this.batchRepository = batchRepository;
        this.birdPurchaseRepository = birdPurchaseRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
        this.birdPopulationService = birdPopulationService;
    }

    public List<BatchResponse> list() {
        return batchRepository.findByFarm(requireFarmId());
    }

    public BatchResponse get(String id) {
        BatchResponse batch = batchRepository.findById(id);
        if (batch == null || !batch.farmId().equals(requireFarmId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found.");
        }
        return batch;
    }

    @Transactional
    public BatchResponse create(BatchCreateRequest request) {
        String farmId = requireFarmId();
        String type = request.type().trim().toUpperCase();

        if (!type.equals("LAYER") && !type.equals("BROILER")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BATCH_TYPE",
                    "Batch type must be LAYER or BROILER.");
        }

        if (!batchRepository.houseBelongsToFarm(request.houseId(), farmId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_HOUSE",
                    "The selected house/pen does not belong to this farm or is archived.");
        }

        if (request.supplierId() != null && !request.supplierId().isBlank()
                && !batchRepository.supplierBelongsToFarm(request.supplierId(), farmId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUPPLIER",
                    "The selected supplier/source does not belong to this farm or is archived.");
        }

        if (request.purchaseCostMinor() % request.initialBirdCount() != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PURCHASE_COST",
                    "Purchase cost must divide evenly into whole minor units per bird.");
        }

        int year = request.placementDate().getYear();
        batchRepository.ensureSequence(type, year);
        int sequence = batchRepository.allocateSequenceNumber(type, year);
        String prefix = type.equals("LAYER") ? "L" : "B";
        String code = String.format("%s-%d-%03d", prefix, year, sequence);

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        try {
            batchRepository.insert(
                    id,
                    farmId,
                    request.houseId(),
                    clean(request.supplierId()),
                    code,
                    type,
                    request.placementDate(),
                    request.initialBirdCount(),
                    request.purchaseCostMinor(),
                    now
            );

            birdPurchaseRepository.insert(
                    UUID.randomUUID().toString(),
                    id,
                    clean(request.supplierId()),
                    request.placementDate(),
                    request.initialBirdCount(),
                    request.purchaseCostMinor() / request.initialBirdCount(),
                    request.purchaseCostMinor(),
                    now
            );
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_CREATE_CONFLICT",
                    "The batch could not be created because one of its references is no longer valid.");
        }

        BatchResponse created = batchRepository.findById(id);
        auditRepository.append(farmId, "CREATE", "BATCH", id, null, null, json(created), now);
        return created;
    }

    @Transactional
    public BatchResponse markSold(String id) {
        BatchResponse current = get(id);
        if (!"BROILER".equals(current.type())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_SOLD_TRANSITION",
                    "Only BROILER batches use the SOLD terminal lifecycle in this stage.");
        }
        if ("SOLD".equals(current.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_ALREADY_SOLD",
                    "This batch is already SOLD.");
        }

        int currentBirds = birdPopulationService.get(current.id(), LocalDate.now()).currentBirds();
        if (currentBirds != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_EMPTY",
                    "A BROILER batch can only be marked SOLD when all birds have been sold.");
        }

        String now = Instant.now().toString();
        batchRepository.updateStatus(id, "SOLD", now);
        BatchResponse sold = batchRepository.findById(id);
        auditRepository.append(current.farmId(), "STATUS_CHANGE", "BATCH", id, null,
                json(current), json(sold), now);
        return sold;
    }

    @Transactional
    public BatchResponse reopen(String id, BatchReopenRequest request) {
        BatchResponse current = get(id);
        if (!"SOLD".equals(current.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_SOLD",
                    "Only a SOLD batch can be reopened.");
        }

        String reason = request.reason().trim();
        if (reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REOPEN_REASON_REQUIRED",
                    "A reason is required to reopen a SOLD batch.");
        }

        String now = Instant.now().toString();
        batchRepository.updateStatus(id, "ACTIVE", now);
        BatchResponse reopened = batchRepository.findById(id);

        auditRepository.append(
                current.farmId(),
                "REOPEN",
                "BATCH",
                id,
                reason,
                json(current),
                json(reopened),
                now
        );
        return reopened;
    }

    private String requireFarmId() {
        var farm = farmRepository.findActive();
        if (farm == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FARM_NOT_FOUND",
                    "Create the farm before managing batches.");
        }
        return farm.id();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize audit data", e);
        }
    }
}
