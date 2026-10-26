package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.DailyOperationsRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DailyOperationsService {
    private final DailyOperationsRepository repository;
    private final BirdPopulationService populationService;
    private final AuditRepository auditRepository;

    public DailyOperationsService(DailyOperationsRepository repository, BirdPopulationService populationService,
                                  AuditRepository auditRepository) {
        this.repository = repository;
        this.populationService = populationService;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public DailyRecordResponse create(String batchId, DailyRecordRequest request) {
        String farmId = requireActiveBatch(batchId);
        LocalDate placement = repository.placementDate(batchId);
        if (request.recordDate().isBefore(placement)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DAILY_RECORD_DATE",
                    "Daily records cannot be dated before the batch placement date.");
        }

        String existingId = repository.dailyRecordId(batchId, request.recordDate());
        String now = java.time.Instant.now().toString();
        boolean created = existingId == null;
        String id = created ? UUID.randomUUID().toString() : existingId;

        validateWaterEntries(request.waterContainers(), farmId);
        try {
            if (created) {
                repository.insertDailyRecord(id, batchId, request.recordDate(), clean(request.notes()), now);
            } else {
                // Null means that this form did not edit the field. Never erase another form's data.
                if (request.notes() != null && !request.notes().isBlank()) {
                    repository.updateNotes(id, clean(request.notes()), now);
                }
                if (request.waterContainers() != null) {
                    repository.deleteWater(id);
                }
            }
            if (request.waterContainers() != null) {
                for (WaterContainerEntryRequest entry : request.waterContainers()) {
                    repository.insertWater(UUID.randomUUID().toString(), id,
                            repository.waterSizeId(entry.containerSizeUnits(), farmId),
                            entry.containerCount(), now);
                }
            }
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "DAILY_RECORD_CONCURRENT_UPDATE",
                    "Another save created or changed this day's record at the same time. Reload the day sheet and retry.");
        }

        auditRepository.append(
                farmId, created ? "CREATE" : "UPDATE", "DAILY_RECORD", id,
                clean(request.notes()), created ? null : "{\"batchId\":\"" + batchId + "\",\"recordDate\":\"" + request.recordDate() + "\"}",
                "{\"batchId\":\"" + batchId + "\",\"recordDate\":\"" + request.recordDate() + "\"}", now
        );
        return get(batchId, request.recordDate());
    }

    private void validateWaterEntries(List<WaterContainerEntryRequest> entries, String farmId) {
        if (entries == null) return;
        for (WaterContainerEntryRequest entry : entries) {
            if (!repository.waterSizeBelongsToFarm(entry.containerSizeUnits(), farmId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WATER_CONTAINER",
                        "Water container size does not belong to the active farm.");
            }
        }
    }

    public DailyRecordResponse get(String batchId, LocalDate date) {
        requireBatch(batchId);
        return build(batchId, date);
    }

    public List<DailyRecordResponse> list(String batchId, LocalDate from, LocalDate to) {
        requireBatch(batchId);
        LocalDate effectiveTo = to == null ? LocalDate.now() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(30) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                    "The start date cannot be after the end date.");
        }
        List<DailyRecordResponse> result = new ArrayList<>();
        for (String value : repository.recordDates(batchId, effectiveFrom, effectiveTo)) {
            result.add(build(batchId, LocalDate.parse(value)));
        }
        return result;
    }

    private DailyRecordResponse build(String batchId, LocalDate date) {
        var population = populationService.get(batchId, date);
        List<DailyFeedUsageResponse> feed = repository.findFeedUsage(batchId, date);
        List<WaterUsageResponse> water = List.of();
        String dailyId = repository.dailyRecordId(batchId, date);
        if (dailyId != null) water = repository.findWater(dailyId);
        long totalWater = water.stream().mapToLong(WaterUsageResponse::totalUnits).sum();
        int mortality = population.mortality() - populationService.get(batchId, date.minusDays(1)).mortality();
        int culling = population.culling() - populationService.get(batchId, date.minusDays(1)).culling();

        return new DailyRecordResponse(dailyId, batchId, date, population.currentBirds(), mortality, culling,
                feed, water, totalWater, repository.findPopulationEvents(batchId, date), repository.notes(batchId, date));
    }

    private String requireActiveBatch(String batchId) {
        requireBatch(batchId);
        if (!"ACTIVE".equals(repository.batchStatus(batchId))) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_ACTIVE",
                    "Daily records cannot be saved for a non-ACTIVE batch.");
        }
        return repository.farmIdForBatch(batchId);
    }

    private void requireBatch(String batchId) {
        if (repository.farmIdForBatch(batchId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found.");
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
