package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.HealthManagementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class HealthManagementService {
    private final HealthManagementRepository repository;
    private final AuditRepository auditRepository;

    public HealthManagementService(HealthManagementRepository repository, AuditRepository auditRepository) {
        this.repository = repository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public HealthRecordResponse addHealth(String batchId, HealthRecordRequest request) {
        String farmId = requireActiveBatch(batchId);
        validateDate(batchId, request.recordDate());

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        String condition = clean(request.conditionProblem());
        String description = clean(request.description());
        String action = clean(request.action());

        repository.insertHealth(id, batchId, request.recordDate(), condition, description, action, now);
        auditRepository.append(
                farmId, "CREATE", "HEALTH_RECORD", id, null, null,
                String.format("{\"batchId\":\"%s\",\"recordDate\":\"%s\",\"conditionProblem\":\"%s\"}",
                        batchId, request.recordDate(), condition), now
        );
        return new HealthRecordResponse(id, batchId, request.recordDate(), condition, description, action);
    }

    public List<HealthRecordResponse> health(String batchId) {
        requireBatch(batchId);
        return repository.findHealth(batchId);
    }

    @Transactional
    public DrugRecordResponse addDrug(String batchId, DrugRecordRequest request) {
        String farmId = requireActiveBatch(batchId);
        validateDate(batchId, request.recordDate());

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        String drug = clean(request.drug());
        String reason = clean(request.reason());

        repository.insertDrug(id, batchId, request.recordDate(), drug, request.quantity(),
                request.costMinor(), reason, now);
        repository.insertExpense(UUID.randomUUID().toString(), farmId, batchId, request.costMinor(),
                request.recordDate(), id, "Drug: " + drug + " - " + reason, now);

        auditRepository.append(
                farmId, "CREATE", "DRUG_RECORD", id, reason, null,
                String.format("{\"batchId\":\"%s\",\"recordDate\":\"%s\",\"drug\":\"%s\",\"costMinor\":%d}",
                        batchId, request.recordDate(), drug, request.costMinor()), now
        );
        return new DrugRecordResponse(id, batchId, request.recordDate(), drug, request.quantity(),
                request.costMinor(), reason);
    }

    public List<DrugRecordResponse> drugs(String batchId) {
        requireBatch(batchId);
        return repository.findDrugs(batchId);
    }

    @Transactional
    public VaccinationRecordResponse addVaccination(String batchId, VaccinationRecordRequest request) {
        String farmId = requireActiveBatch(batchId);
        validateDate(batchId, request.recordDate());

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        String vaccine = clean(request.vaccine());
        String dose = clean(request.dose());
        String notes = cleanNullable(request.notes());

        repository.insertVaccination(id, batchId, request.recordDate(), vaccine, dose,
                request.quantity(), notes, now);
        auditRepository.append(
                farmId, "CREATE", "VACCINATION_RECORD", id, notes, null,
                String.format("{\"batchId\":\"%s\",\"recordDate\":\"%s\",\"vaccine\":\"%s\",\"quantity\":%d}",
                        batchId, request.recordDate(), vaccine, request.quantity()), now
        );
        return new VaccinationRecordResponse(id, batchId, request.recordDate(), vaccine, dose,
                request.quantity(), notes);
    }

    public List<VaccinationRecordResponse> vaccinations(String batchId) {
        requireBatch(batchId);
        return repository.findVaccinations(batchId);
    }

    private String requireActiveBatch(String batchId) {
        requireBatch(batchId);
        if (!"ACTIVE".equals(repository.batchStatus(batchId))) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_ACTIVE",
                    "Health records cannot be added to a non-ACTIVE batch.");
        }
        return repository.farmIdForBatch(batchId);
    }

    private void requireBatch(String batchId) {
        if (repository.farmIdForBatch(batchId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found.");
        }
    }

    private void validateDate(String batchId, LocalDate date) {
        if (date.isBefore(repository.placementDate(batchId))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_HEALTH_RECORD_DATE",
                    "Health records cannot be dated before the batch placement date.");
        }
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
