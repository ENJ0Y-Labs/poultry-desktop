package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.dto.BatchCreateRequest;
import com.grantinofarms.poultry.dto.BatchSetupUpdateRequest;
import com.grantinofarms.poultry.dto.BatchResponse;
import com.grantinofarms.poultry.dto.BatchReopenRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.BatchRepository;
import com.grantinofarms.poultry.repository.BirdPurchaseRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.security.MessageDigest;
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
    private final JdbcTemplate jdbc;

    public BatchService(FarmRepository farmRepository,
                        BatchRepository batchRepository,
                        BirdPurchaseRepository birdPurchaseRepository,
                        AuditRepository auditRepository,
                        ObjectMapper objectMapper,
                        BirdPopulationService birdPopulationService, JdbcTemplate jdbc) {
        this.farmRepository = farmRepository;
        this.batchRepository = batchRepository;
        this.birdPurchaseRepository = birdPurchaseRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
        this.birdPopulationService = birdPopulationService;
        this.jdbc = jdbc;
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
    public BatchResponse create(BatchCreateRequest request) { return create(request, UUID.randomUUID().toString()); }

    @Transactional
    public BatchResponse create(BatchCreateRequest request, String idempotencyKey) {
        String farmId = requireFarmId();
        String type = request.type().trim().toUpperCase();

        if (!List.of("LAYER", "BROILER", "TURKEY").contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BATCH_TYPE",
                    "Batch type must be LAYER, BROILER, or TURKEY.");
        }

        if (!batchRepository.houseBelongsToFarm(request.houseId(), farmId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_HOUSE",
                    "The selected house/pen does not belong to this farm or is archived.");
        }

        if (request.purchaseCostRecorded() == null && request.purchaseCostMinor() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PURCHASE_COST_MODE",
                    "Choose whether purchase information is being recorded.");
        }
        boolean purchaseCostRecorded = request.purchaseCostRecorded() == null
                || request.purchaseCostRecorded();
        if ((purchaseCostRecorded && request.purchaseCostMinor() == null)
                || (!purchaseCostRecorded && request.purchaseCostMinor() != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PURCHASE_COST_MODE",
                    "Record purchase cost requires an actual cost. Existing stock must not include a purchase cost.");
        }

        if (request.supplierId() != null && !request.supplierId().isBlank()
                && !batchRepository.supplierBelongsToFarm(request.supplierId(), farmId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUPPLIER",
                    "The selected supplier/source does not belong to this farm or is archived.");
        }

        String key = clean(idempotencyKey);
        if (key == null || key.length() > 128) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "A valid Idempotency-Key header is required to create a batch.");
        String hash = requestHash(request);
        String now = Instant.now().toString();
        int claimed = jdbc.update("INSERT OR IGNORE INTO batch_create_requests (farm_id,idempotency_key,request_hash,batch_id,created_at) VALUES (?,?,?,NULL,?)", farmId, key, hash, now);
        if (claimed == 0) {
            var existing = jdbc.query("SELECT request_hash,batch_id FROM batch_create_requests WHERE farm_id=? AND idempotency_key=?", (rs,n) -> new String[]{rs.getString(1),rs.getString(2)}, farmId, key).stream().findFirst().orElse(null);
            if (existing == null || !hash.equals(existing[0])) throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "This retry key belongs to another request. Check batch history before starting a new registration.");
            if (existing[1] == null) throw new ApiException(HttpStatus.CONFLICT, "BATCH_CREATE_IN_PROGRESS", "This batch request is still processing. Refresh the batch list before retrying.");
            BatchResponse original = batchRepository.findById(existing[1]);
            if (original == null || !original.farmId().equals(farmId)) throw new ApiException(HttpStatus.CONFLICT, "BATCH_CREATE_RESULT_UNAVAILABLE", "The original batch result is unavailable. Check batch history before retrying.");
            return original;
        }
        int year = request.placementDate().getYear();
        batchRepository.ensureSequence(type, year);
        int sequence = batchRepository.allocateSequenceNumber(type, year);
        String prefix = switch (type) { case "LAYER" -> "L"; case "BROILER" -> "B"; default -> "TY"; };
        String code = String.format("%s-%d-%03d", prefix, year, sequence);

        String id = UUID.randomUUID().toString();
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
                    request.populationCountStatus() == null ? "ESTIMATED" : request.populationCountStatus(),
                    now
            );

            if (purchaseCostRecorded) {
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
            }
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_CREATE_CONFLICT",
                    "The batch could not be created because one of its references is no longer valid.");
        }

        BatchResponse created = batchRepository.findById(id);
        auditRepository.append(farmId, "CREATE", "BATCH", id, null, null, json(created), now);
        jdbc.update("UPDATE batch_create_requests SET batch_id=? WHERE farm_id=? AND idempotency_key=?", id, farmId, key);
        return created;
    }

    @Transactional
    public BatchResponse cancel(String id, String suppliedReason) {
        BatchResponse current = get(id);
        if (!"ACTIVE".equals(current.status())) throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_CANCELLABLE", "Only an active batch can be cancelled.");
        String reason = clean(suppliedReason);
        if (reason == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CANCELLATION_REASON_REQUIRED", "A reason is required to cancel an accidental batch.");
        String now = Instant.now().toString();
        if (batchRepository.transitionStatus(id, "ACTIVE", "CANCELLED", now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_CANCELLABLE",
                    "The batch changed while cancellation was being processed. Refresh its status and try again.");
        }

        // Claim the lifecycle transition before checking references. This obtains
        // SQLite's write lock so a concurrent operational write cannot slip
        // between the dependency check and cancellation. Any rejection rolls
        // this status update back with the transaction.
        List<String> references = new java.util.ArrayList<>();
        List<String> tables = jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'", String.class);
        for (String table : tables) {
            if (List.of("batches", "audit_logs", "batch_create_requests").contains(table) || !table.matches("[A-Za-z0-9_]+")) continue;
            List<String> columns = jdbc.query("PRAGMA table_info(\"" + table + "\")", (rs,n) -> rs.getString("name"));
            if (!columns.contains("batch_id")) continue;
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM \"" + table + "\" WHERE batch_id=?", Integer.class, id);
            if (count != null && count > 0) references.add(table + " (" + count + ")");
        }
        if (!references.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "BATCH_HAS_DEPENDENT_RECORDS", "This batch has dependent records and cannot be cancelled: " + String.join(", ", references) + ". Use the audited correction or reversal workflow where applicable.");
        BatchResponse cancelled = batchRepository.findById(id);
        auditRepository.append(current.farmId(), "CANCEL", "BATCH", id, reason, json(current), json(cancelled), now);
        return cancelled;
    }

    private String requestHash(BatchCreateRequest request) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(request))); }
        catch (Exception e) { throw new IllegalStateException("Unable to fingerprint batch request.", e); }
    }
    @Transactional
    public BatchResponse updateSetup(String id, BatchSetupUpdateRequest request) {
        BatchResponse current = get(id);
        if (!"ACTIVE".equals(current.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_SETUP_LOCKED",
                    "Only active batches can have setup references edited.");
        }
        String farmId = current.farmId();
        if (!batchRepository.houseBelongsToFarm(request.houseId(), farmId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_HOUSE",
                    "The selected house/pen does not belong to this farm or is archived.");
        }
        String supplierId = clean(request.supplierId());
        if (supplierId != null && !batchRepository.supplierBelongsToFarm(supplierId, farmId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUPPLIER",
                    "The selected supplier/source does not belong to this farm or is archived.");
        }
        if (current.purchaseCostMinor() != null
                && !java.util.Objects.equals(current.supplierId(), supplierId)) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_PURCHASE_SOURCE_LOCKED",
                    "The supplier/source cannot change after an original bird purchase cost has been recorded.");
        }
        String now = Instant.now().toString();
        batchRepository.updateSetup(id, farmId, request.houseId(), supplierId, now);
        BatchResponse updated = batchRepository.findById(id);
        auditRepository.append(farmId, "UPDATE", "BATCH", id, null,
                json(current), json(updated), now);
        return updated;
    }

    @Transactional
    public BatchResponse markSold(String id) {
        BatchResponse current = get(id);
        if (!List.of("BROILER", "TURKEY").contains(current.type())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_SOLD_TRANSITION",
                    "Only meat-production batches (BROILER or TURKEY) use the SOLD terminal lifecycle.");
        }
        if ("SOLD".equals(current.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_ALREADY_SOLD",
                    "This batch is already SOLD.");
        }

        int currentBirds = birdPopulationService.get(current.id(), LocalDate.now()).currentBirds();
        if (currentBirds != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_EMPTY",
                    "A BROILER or TURKEY batch can only be marked SOLD when its calculated population reaches zero.");
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
