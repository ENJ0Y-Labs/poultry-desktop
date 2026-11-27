package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.dto.BatchResponse;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.BatchRepository;
import com.grantinofarms.poultry.repository.FarmIncidentRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.HouseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FarmIncidentService {
    private static final Set<String> TYPES = Set.of("SUSPECTED_DISEASE", "UNUSUAL_MORTALITY",
            "WATER_FAILURE", "FEED_FAILURE", "FIRE", "FLOOD", "SECURITY", "EQUIPMENT_DAMAGE", "OTHER");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private final FarmIncidentRepository incidents;
    private final FarmRepository farms;
    private final BatchRepository batches;
    private final HouseRepository houses;
    private final AuditRepository audit;
    private final ObjectMapper mapper;

    public FarmIncidentService(FarmIncidentRepository incidents, FarmRepository farms,
                               BatchRepository batches, HouseRepository houses,
                               AuditRepository audit, ObjectMapper mapper) {
        this.incidents = incidents;
        this.farms = farms;
        this.batches = batches;
        this.houses = houses;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> create(IncidentRequest request) {
        RoleAuthorization.requireReportSubmitter();
        if (request == null || request.type() == null || !TYPES.contains(request.type())
                || request.severity() == null || !SEVERITIES.contains(request.severity())
                || request.title() == null || request.title().isBlank() || request.title().length() > 160
                || request.description() == null || request.description().isBlank()
                || request.description().length() > 4000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INCIDENT",
                    "Incident type, severity, title, and description are required.");
        }
        var farm = farms.findActive();
        String eventAt = normalizeEventAt(request.eventAt(), farm.timezone());
        String actor = CurrentUserContext.get();
        BatchResponse batch = request.batchId() == null ? null : batches.findById(request.batchId());
        if (request.batchId() != null && (batch == null || !farm.id().equals(batch.farmId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INCIDENT_BATCH",
                    "The selected batch does not belong to this farm.");
        }
        var house = request.houseId() == null ? null : houses.findById(request.houseId());
        if (request.houseId() != null && (house == null || !farm.id().equals(house.farmId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INCIDENT_HOUSE",
                    "The selected house does not belong to this farm.");
        }
        if (batch != null && house != null && !batch.houseId().equals(house.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INCIDENT_SCOPE_MISMATCH",
                    "The selected batch does not belong to the selected house.");
        }
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        incidents.insert(id, farm.id(), actor, request.type(), request.severity(),
                request.title().trim(), request.description().trim(), request.batchId(),
                request.houseId(), eventAt, now);
        incidents.appendEvent(UUID.randomUUID().toString(), id, actor, "INCIDENT_REPORTED",
                json(Map.of("severity", request.severity(), "type", request.type())), now);
        incidents.notifyManagers(farm.id(), id, request.severity(), request.title().trim(),
                request.description().trim(), now);
        audit.append(farm.id(), "INCIDENT_CREATE", "FARM_INCIDENT", id,
                "Emergency incident reported", null,
                json(Map.of("type", request.type(), "severity", request.severity())), now);
        return detail(requireIncident(id));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        RoleAuthorization.requireReportSubmitter();
        var farm = farms.findActive();
        String actor = CurrentUserContext.get();
        boolean manager = "MANAGER".equals(CurrentUserContext.getRole());
        return incidents.list(farm.id(), actor, manager).stream()
                .map(row -> detail(row)).toList();
    }

    @Transactional
    public Map<String, Object> acknowledge(String id) {
        RoleAuthorization.requireManager();
        Map<String, Object> incident = requireIncident(id);
        String now = Instant.now().toString();
        if (incidents.acknowledge(id, CurrentUserContext.get(), now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "INCIDENT_NOT_OPEN",
                    "Only an open incident can be acknowledged.");
        }
        incidents.appendEvent(UUID.randomUUID().toString(), id, CurrentUserContext.get(),
                "INCIDENT_ACKNOWLEDGED", "{}", now);
        audit.append(String.valueOf(incident.get("farmId")), "INCIDENT_ACKNOWLEDGE", "FARM_INCIDENT",
                id, "Manager acknowledged emergency incident", null, "{}", now);
        return detail(requireIncident(id));
    }

    @Transactional
    public Map<String, Object> resolve(String id, String notes) {
        RoleAuthorization.requireManager();
        Map<String, Object> incident = requireIncident(id);
        String reason = notes == null ? "" : notes.trim();
        if (reason.isBlank() || reason.length() > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RESOLUTION_NOTES_REQUIRED",
                    "Enter resolution notes of 1 to 2,000 characters.");
        }
        String now = Instant.now().toString();
        if (incidents.resolve(id, CurrentUserContext.get(), reason, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "INCIDENT_ALREADY_RESOLVED",
                    "This incident has already been resolved.");
        }
        incidents.appendEvent(UUID.randomUUID().toString(), id, CurrentUserContext.get(),
                "INCIDENT_RESOLVED", json(Map.of("resolutionNotes", reason)), now);
        audit.append(String.valueOf(incident.get("farmId")), "INCIDENT_RESOLVE", "FARM_INCIDENT",
                id, "Manager resolved emergency incident", null,
                json(Map.of("resolutionNotes", reason)), now);
        return detail(requireIncident(id));
    }

    @Transactional
    public Map<String, Object> escalate(String id, String notes) {
        RoleAuthorization.requireManager();
        Map<String, Object> incident = requireIncident(id);
        String reason = notes == null ? "" : notes.trim();
        if (reason.isBlank() || reason.length() > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ESCALATION_NOTES_REQUIRED",
                    "Enter escalation notes of 1 to 2,000 characters.");
        }
        if ("RESOLVED".equals(incident.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "INCIDENT_ALREADY_RESOLVED",
                    "A resolved incident cannot be escalated.");
        }
        String now = Instant.now().toString();
        incidents.appendEvent(UUID.randomUUID().toString(), id, CurrentUserContext.get(),
                "INCIDENT_ESCALATED", json(Map.of("reason", reason)), now);
        incidents.notifyManagers(String.valueOf(incident.get("farmId")), id,
                String.valueOf(incident.get("severity")), "ESCALATED: " + incident.get("title"),
                reason, now);
        audit.append(String.valueOf(incident.get("farmId")), "INCIDENT_ESCALATE", "FARM_INCIDENT",
                id, "Manager escalated emergency incident", null,
                json(Map.of("reason", reason)), now);
        return detail(requireIncident(id));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> notifications() {
        RoleAuthorization.requireReportSubmitter();
        return incidents.notifications(CurrentUserContext.get());
    }

    @Transactional
    public void markNotificationRead(String id) {
        RoleAuthorization.requireReportSubmitter();
        incidents.markNotificationRead(id, CurrentUserContext.get(), Instant.now().toString());
    }

    private Map<String, Object> requireIncident(String id) {
        Map<String, Object> row = incidents.find(id);
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND",
                "Incident was not found.");
        if (!"MANAGER".equals(CurrentUserContext.getRole())
                && !CurrentUserContext.get().equals(row.get("reportedBy"))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incident was not found.");
        }
        return row;
    }

    private Map<String, Object> detail(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("history", incidents.events(String.valueOf(row.get("id"))));
        return result;
    }

    private String normalizeEventAt(String value, String timezone) {
        if (value == null || value.isBlank()) return Instant.now().toString();
        try {
            Instant instant = LocalDateTime.parse(value).atZone(ZoneId.of(timezone)).toInstant();
            if (instant.isAfter(Instant.now().plusSeconds(300))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FUTURE_INCIDENT_TIME",
                        "Incident time cannot be in the future.");
            }
            return instant.toString();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INCIDENT_TIME",
                    "Enter a valid incident date and time in the farm timezone.");
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { return "{}"; }
    }

    public record IncidentRequest(String type, String severity, String title, String description,
                                  String batchId, String houseId, String eventAt) {}
}
