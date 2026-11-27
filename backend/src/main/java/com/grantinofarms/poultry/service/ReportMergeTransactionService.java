package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.BirdPopulationRepository;
import com.grantinofarms.poultry.repository.BatchRepository;
import com.grantinofarms.poultry.repository.ReportSubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportMergeTransactionService {
    private final ReportSubmissionRepository submissions;
    private final BatchRepository batches;
    private final BirdPopulationRepository populationEvents;
    private final BirdPopulationService population;
    private final DailyOperationsService dailyOperations;
    private final HealthManagementService health;
    private final EggManagementService eggs;
    private final BroilerProductionService broiler;
    private final FeedService feed;
    private final ExpenseService expenses;
    private final AuditRepository audit;
    private final ObjectMapper mapper;

    public ReportMergeTransactionService(ReportSubmissionRepository submissions, BatchRepository batches,
            BirdPopulationRepository populationEvents, BirdPopulationService population,
            DailyOperationsService dailyOperations, HealthManagementService health,
            EggManagementService eggs, BroilerProductionService broiler, FeedService feed,
            ExpenseService expenses, AuditRepository audit, ObjectMapper mapper) {
        this.submissions = submissions;
        this.batches = batches;
        this.populationEvents = populationEvents;
        this.population = population;
        this.dailyOperations = dailyOperations;
        this.health = health;
        this.eggs = eggs;
        this.broiler = broiler;
        this.feed = feed;
        this.expenses = expenses;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> merge(String id, String operationKey) {
        RoleAuthorization.requireManager();
        if (operationKey == null || !operationKey.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid Idempotency-Key header is required for merging.");
        }
        String actor = CurrentUserContext.get();
        Map<String, Object> header = requireHeader(id);
        if ("MERGED".equals(header.get("lifecycleStatus"))) return detail(header);
        if (actor.equals(header.get("authorId"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SELF_MERGE_FORBIDDEN",
                    "A Manager cannot merge their own submission.");
        }
        if (!"APPROVED".equals(header.get("lifecycleStatus"))) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_NOT_APPROVED",
                    "Only an approved report version can be merged.");
        }
        int version = ((Number) header.get("submittedVersion")).intValue();
        if (version != ((Number) header.get("currentVersion")).intValue()
                || header.get("decisionVersion") == null
                || ((Number) header.get("decisionVersion")).intValue() != version) {
            throw new ApiException(HttpStatus.CONFLICT, "APPROVAL_VERSION_MISMATCH",
                    "The approved version is no longer the current submitted version.");
        }
        if (!"PENDING_REVIEW".equals(submissions.findLatestCheckRunOutcome(id, version))) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_RECHECK_REQUIRED",
                    "The latest automated checks do not permit merging.");
        }

        if (submissions.beginMerge(id, version, operationKey, Instant.now().toString()) != 1) {
            Map<String, Object> refreshed = requireHeader(id);
            if ("MERGED".equals(refreshed.get("lifecycleStatus"))) return detail(refreshed);
            throw new ApiException(HttpStatus.CONFLICT, "MERGE_ALREADY_IN_PROGRESS",
                    "This report changed before the merge could begin. Refresh the review centre.");
        }

        JsonNode payload = versionPayload(id, version);
        String reportType = String.valueOf(header.get("reportType"));
        String batchId = text(payload, "batchId");
        if (!"EXPENSE".equals(reportType)) {
            var batch = batchId == null ? null : batches.findById(batchId);
            if (batch == null || !String.valueOf(header.get("farmId")).equals(batch.farmId())
                    || !"ACTIVE".equals(batch.status())) {
                throw new ApiException(HttpStatus.CONFLICT, "BATCH_NOT_AVAILABLE",
                        "The batch is no longer active on this farm. The report was not merged.");
            }
        }

        Object domainResult;
        String recordType = reportType;
        String recordId;
        switch (reportType) {
            case "DAILY_OPERATIONS" -> {
                ObjectNode body = dated(payload, "recordDate");
                String waterNotes = text(payload, "waterNotes");
                if (waterNotes != null) body.put("notes", appendNote(text(payload, "notes"), "Water: " + waterNotes));
                domainResult = dailyOperations.create(batchId, read(body, DailyRecordRequest.class));
            }
            case "MORTALITY" -> {
                population.addMortality(batchId, read(payload, BirdPopulationEventRequest.class));
                recordType = "BIRD_POPULATION_EVENT";
                recordId = populationEvents.latestEventId(batchId, LocalDate.parse(text(payload, "eventDate")), "MORTALITY");
                insertLinkAndComplete(id, version, recordType, recordId, operationKey, header);
                return detail(requireHeader(id));
            }
            case "CULLING" -> {
                population.addCulling(batchId, read(payload, BirdPopulationEventRequest.class));
                recordType = "BIRD_POPULATION_EVENT";
                recordId = populationEvents.latestEventId(batchId, LocalDate.parse(text(payload, "eventDate")), "CULLING");
                insertLinkAndComplete(id, version, recordType, recordId, operationKey, header);
                return detail(requireHeader(id));
            }
            case "HEALTH_RECORD" -> domainResult = health.addHealth(batchId, read(dated(payload, "recordDate"), HealthRecordRequest.class));
            case "DRUG_TREATMENT" -> domainResult = health.addDrug(batchId, read(dated(payload, "recordDate"), DrugRecordRequest.class));
            case "VACCINATION" -> domainResult = health.addVaccination(batchId, read(dated(payload, "recordDate"), VaccinationRecordRequest.class));
            case "EGG_COLLECTION" -> domainResult = eggs.addCollection(batchId, read(dated(payload, "recordDate"), EggCollectionRequest.class));
            case "WEIGHT_RECORD" -> domainResult = broiler.addWeight(batchId, read(dated(payload, "recordDate"), WeightRecordRequest.class));
            case "FEED_USAGE" -> domainResult = feed.use(batchId, read(dated(payload, "usageDate"), FeedUsageRequest.class));
            case "EXPENSE" -> domainResult = expenses.create(read(dated(payload, "occurredDate"), ExpenseRequest.class));
            case "BIRD_SALE" -> domainResult = broiler.addSale(batchId, read(dated(payload, "recordDate"), BirdSaleRequest.class));
            case "EGG_SALE" -> domainResult = eggs.addSale(batchId, read(dated(payload, "recordDate"), EggSaleRequest.class));
            default -> throw new ApiException(HttpStatus.CONFLICT, "UNSUPPORTED_REPORT_TYPE",
                    "No official ledger adapter exists for this report type.");
        }
        recordId = mapper.valueToTree(domainResult).path("id").asText(null);
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalStateException("The domain service did not return the created record identifier.");
        }
        insertLinkAndComplete(id, version, recordType, recordId, operationKey, header);
        return detail(requireHeader(id));
    }

    private void insertLinkAndComplete(String id, int version, String recordType, String recordId,
                                       String operationKey, Map<String, Object> header) {
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalStateException("The official domain record identifier could not be resolved.");
        }
        String now = Instant.now().toString();
        submissions.insertLink(id, version, recordType, recordId, now);
        if (submissions.completeMerge(id, operationKey, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "MERGE_STATE_CHANGED",
                    "The report merge state changed unexpectedly. The transaction was rolled back.");
        }
        submissions.appendEvent(id, version, CurrentUserContext.get(), "MERGED",
                "{\"recordType\":\"" + recordType + "\",\"recordId\":\"" + recordId + "\"}", now);
        audit.append(String.valueOf(header.get("farmId")), "MERGE", "REPORT_SUBMISSION", id,
                "Approved report merged into official farm records", null,
                "{\"version\":" + version + ",\"recordType\":\"" + recordType
                        + "\",\"recordId\":\"" + recordId + "\"}", now);
    }

    private ObjectNode dated(JsonNode payload, String dateField) {
        ObjectNode copy = payload.deepCopy();
        if (!copy.has(dateField) && copy.has("eventDate")) copy.set(dateField, copy.get("eventDate"));
        return copy;
    }

    private <T> T read(JsonNode payload, Class<T> type) {
        try {
            return mapper.readerFor(type).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(payload.traverse(mapper));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_APPROVED_REPORT",
                    "The approved report no longer matches the official record requirements.");
        }
    }

    private JsonNode versionPayload(String id, int version) {
        return submissions.findVersions(id).stream()
                .filter(row -> ((Number) row.get("version")).intValue() == version)
                .map(row -> (JsonNode) row.get("payload"))
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "SUBMISSION_VERSION_NOT_FOUND", "The approved report version was not found."));
    }

    private Map<String, Object> requireHeader(String id) {
        Map<String, Object> header = submissions.findHeader(id);
        if (header == null) throw new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND",
                "Report submission was not found.");
        return header;
    }

    private Map<String, Object> detail(Map<String, Object> header) {
        String id = String.valueOf(header.get("id"));
        int version = header.get("submittedVersion") instanceof Number n
                ? n.intValue() : ((Number) header.get("currentVersion")).intValue();
        Map<String, Object> result = new LinkedHashMap<>(header);
        result.put("versions", submissions.findVersions(id));
        result.put("history", submissions.findEvents(id));
        result.put("comments", submissions.findComments(id));
        result.put("checks", submissions.findChecks(id, version));
        result.put("decisions", submissions.findDecisions(id));
        result.put("assignments", submissions.findAssignments(id));
        result.put("links", submissions.findLinks(id));
        result.put("attachments", submissions.findAttachments(id, version));
        return result;
    }

    private String text(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText().trim();
    }

    private String appendNote(String existing, String addition) {
        return existing == null || existing.isBlank() ? addition : existing + "\n" + addition;
    }
}
