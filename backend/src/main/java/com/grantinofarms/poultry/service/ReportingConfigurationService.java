package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.BatchRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.HouseRepository;
import com.grantinofarms.poultry.repository.ReportingConfigurationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReportingConfigurationService {
    private static final Set<String> REPORT_TYPES = Set.of("DAILY_OPERATIONS","MORTALITY","CULLING",
            "HEALTH_RECORD","DRUG_TREATMENT","VACCINATION","EGG_COLLECTION","WEIGHT_RECORD",
            "FEED_USAGE","EXPENSE","BIRD_SALE","EGG_SALE");
    private final ReportingConfigurationRepository reporting;
    private final FarmRepository farms;
    private final BatchRepository batches;
    private final HouseRepository houses;

    public ReportingConfigurationService(ReportingConfigurationRepository reporting, FarmRepository farms,
                                         BatchRepository batches, HouseRepository houses) {
        this.reporting = reporting;
        this.farms = farms;
        this.batches = batches;
        this.houses = houses;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> schedules() {
        RoleAuthorization.requireManager();
        return reporting.schedules(farms.findActive().id());
    }

    @Transactional
    public Map<String, Object> createSchedule(ScheduleRequest request) {
        RoleAuthorization.requireManager();
        var farm = farms.findActive();
        if (request == null || !REPORT_TYPES.contains(request.reportType())
                || !Set.of("FARM","HOUSE","BATCH").contains(request.scopeType())
                || !Set.of("DAILY","WEEKLY").contains(request.frequency())
                || request.graceMinutes() < 0 || request.graceMinutes() > 1440) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_SCHEDULE",
                    "Report type, scope, frequency, and grace period are invalid.");
        }
        if ("WEEKLY".equals(request.frequency()) && (request.dayOfWeek() == null
                || request.dayOfWeek() < 1 || request.dayOfWeek() > 7)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE_DAY",
                    "Weekly schedules require a day of week from 1 (Monday) to 7 (Sunday).");
        }
        if ("DAILY".equals(request.frequency()) && request.dayOfWeek() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE_DAY",
                    "Daily schedules must not specify a day of week.");
        }
        try { LocalTime.parse(request.dueTime()); }
        catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DUE_TIME", "Enter a valid local due time (HH:mm).");
        }
        validateScope(farm.id(), request.scopeType(), request.scopeId());
        if (request.batchType() != null && !Set.of("LAYER","BROILER","TURKEY").contains(request.batchType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BATCH_TYPE", "Batch type is invalid.");
        }
        if ("BATCH".equals(request.scopeType()) && request.batchType() != null) {
            var scopedBatch = batches.findById(request.scopeId());
            if (scopedBatch != null && !request.batchType().equals(scopedBatch.type())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SCHEDULE_BATCH_TYPE_MISMATCH",
                        "The selected batch type does not match the selected batch.");
            }
        }
        String id = UUID.randomUUID().toString();
        reporting.insertSchedule(id, farm.id(), request.reportType(), request.scopeType(),
                request.scopeId(), request.batchType(), request.frequency(), request.dueTime(),
                request.dayOfWeek(), request.graceMinutes(), CurrentUserContext.get(), Instant.now().toString());
        return reporting.schedules(farm.id()).stream().filter(row -> id.equals(row.get("id"))).findFirst().orElseThrow();
    }

    @Transactional
    public void setScheduleEnabled(String id, boolean enabled) {
        RoleAuthorization.requireManager();
        if (reporting.updateScheduleEnabled(id, farms.findActive().id(), enabled, Instant.now().toString()) != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Reporting schedule was not found.");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> thresholds() {
        RoleAuthorization.requireManager();
        return reporting.thresholds(farms.findActive().id());
    }

    @Transactional
    public Map<String, Object> createThreshold(ThresholdRequest request) {
        RoleAuthorization.requireManager();
        var farm = farms.findActive();
        if (request == null || !Set.of("UNUSUAL_MORTALITY_COUNT","UNUSUAL_MORTALITY_PERCENT").contains(request.code())
                || !Set.of("FARM","HOUSE","BATCH","BATCH_TYPE").contains(request.scopeType())
                || request.value() <= 0 || !Double.isFinite(request.value())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_THRESHOLD",
                    "Threshold code, scope, and positive numeric value are required.");
        }
        String expectedUnit = "UNUSUAL_MORTALITY_COUNT".equals(request.code()) ? "BIRDS" : "PERCENT";
        if (!expectedUnit.equals(request.unit())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_THRESHOLD_UNIT",
                    "Threshold unit must be " + expectedUnit + ".");
        }
        if ("BATCH_TYPE".equals(request.scopeType())) {
            if (!Set.of("LAYER","BROILER","TURKEY").contains(request.batchType())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BATCH_TYPE", "Choose LAYER, BROILER, or TURKEY.");
            }
        } else validateScope(farm.id(), request.scopeType(), request.scopeId());
        String id = UUID.randomUUID().toString();
        reporting.insertThreshold(id, farm.id(), request.code(), request.scopeType(), request.scopeId(),
                request.batchType(), request.value(), request.unit(), CurrentUserContext.get(), Instant.now().toString());
        return reporting.thresholds(farm.id()).stream().filter(row -> id.equals(row.get("id"))).findFirst().orElseThrow();
    }

    @Transactional
    public void setThresholdEnabled(String id, boolean enabled) {
        RoleAuthorization.requireManager();
        if (reporting.updateThresholdEnabled(id, farms.findActive().id(), enabled, Instant.now().toString()) != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "THRESHOLD_NOT_FOUND", "Reporting threshold was not found.");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> missingReports() {
        RoleAuthorization.requireManager();
        var farm = farms.findActive();
        ZoneId zone = ZoneId.of(farm.timezone());
        LocalDateTime now = LocalDateTime.now(zone);
        List<Map<String, Object>> missing = new java.util.ArrayList<>();
        for (Map<String, Object> schedule : reporting.schedules(farm.id())) {
            if (!Boolean.TRUE.equals(schedule.get("enabled"))) continue;
            boolean weekly = "WEEKLY".equals(schedule.get("frequency"));
            LocalTime dueTime = LocalTime.parse(String.valueOf(schedule.get("dueTime")));
            LocalDate latestDate = now.toLocalDate();
            if (weekly) {
                int day = ((Number) schedule.get("dayOfWeek")).intValue();
                int diff = Math.floorMod(now.getDayOfWeek().getValue() - day, 7);
                latestDate = latestDate.minusDays(diff);
            }
            LocalDateTime latestDue = LocalDateTime.of(latestDate, dueTime);
            long grace = ((Number) schedule.get("graceMinutes")).longValue();
            if (!now.isAfter(latestDue.plusMinutes(grace))) {
                latestDate = latestDate.minusDays(weekly ? 7 : 1);
            }
            String scopeType = String.valueOf(schedule.get("scopeType"));
            String scopeId = (String) schedule.get("scopeId");
            String batchType = (String) schedule.get("batchType");
            int periods = weekly ? 8 : 30;
            for (int period = 0; period < periods; period++) {
                LocalDate dueDate = latestDate.minusDays((long) period * (weekly ? 7 : 1));
                LocalDateTime dueAt = LocalDateTime.of(dueDate, dueTime);
                LocalDateTime graceUntil = dueAt.plusMinutes(grace);
                LocalDateTime windowEnd = dueAt.plusDays(weekly ? 7 : 1);
                if (!now.isAfter(graceUntil)) continue;
                Instant scheduleCreatedAt = Instant.parse(String.valueOf(schedule.get("createdAt")));
                Instant scheduleUpdatedAt = Instant.parse(String.valueOf(schedule.get("updatedAt")));
                Instant effectiveFrom = scheduleUpdatedAt.isAfter(scheduleCreatedAt)
                        ? scheduleUpdatedAt : scheduleCreatedAt;
                if (dueAt.atZone(zone).toInstant().isBefore(effectiveFrom)) continue;
                if (reporting.hasSubmissionInWindow(farm.id(), String.valueOf(schedule.get("reportType")),
                        scopeType, scopeId, batchType,
                        dueAt.atZone(zone).toInstant().toString(), windowEnd.atZone(zone).toInstant().toString())) continue;
                Map<String, Object> row = new LinkedHashMap<>(schedule);
                row.put("dueAt", dueAt.atZone(zone).toInstant().toString());
                row.put("graceUntil", graceUntil.atZone(zone).toInstant().toString());
                row.put("status", "MISSING");
                row.put("note", "No report was submitted for this schedule. Missing is not zero activity.");
                missing.add(row);
            }
        }
        return missing;
    }

    private void validateScope(String farmId, String scopeType, String scopeId) {
        if ("FARM".equals(scopeType)) {
            if (scopeId != null && !scopeId.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST,
                    "INVALID_SCOPE_ID", "Farm-wide schedules must not include a scope ID.");
            return;
        }
        if (scopeId == null || scopeId.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST,
                "SCOPE_ID_REQUIRED", "Choose a house or batch for this scope.");
        if ("BATCH".equals(scopeType)) {
            var batch = batches.findById(scopeId);
            if (batch == null || !farmId.equals(batch.farmId())) throw new ApiException(HttpStatus.BAD_REQUEST,
                    "INVALID_SCOPE_ID", "The selected batch does not belong to this farm.");
        } else {
            var house = houses.findById(scopeId);
            if (house == null || !farmId.equals(house.farmId())) throw new ApiException(HttpStatus.BAD_REQUEST,
                    "INVALID_SCOPE_ID", "The selected house does not belong to this farm.");
        }
    }

    public record ScheduleRequest(String reportType, String scopeType, String scopeId, String batchType,
                                  String frequency, String dueTime, Integer dayOfWeek, int graceMinutes) {}
    public record ThresholdRequest(String code, String scopeType, String scopeId, String batchType,
                                   double value, String unit) {}
}
