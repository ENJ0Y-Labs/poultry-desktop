package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.repository.BatchRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.FeedRepository;
import com.grantinofarms.poultry.repository.ReportSubmissionRepository;
import com.grantinofarms.poultry.repository.ReportConflictRepository;
import com.grantinofarms.poultry.repository.ReportingConfigurationRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReportCheckService {
    private final ReportSubmissionRepository submissions;
    private final BatchRepository batches;
    private final FeedRepository feeds;
    private final FarmRepository farms;
    private final BirdPopulationService population;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final ReportingConfigurationRepository reporting;
    private final ReportConflictRepository conflicts;

    public ReportCheckService(ReportSubmissionRepository submissions, BatchRepository batches,
                              FeedRepository feeds, FarmRepository farms, BirdPopulationService population,
                              ObjectMapper mapper, Validator validator,
                              ReportingConfigurationRepository reporting,
                              ReportConflictRepository conflicts) {
        this.submissions = submissions;
        this.batches = batches;
        this.feeds = feeds;
        this.farms = farms;
        this.population = population;
        this.mapper = mapper;
        this.validator = validator;
        this.reporting = reporting;
        this.conflicts = conflicts;
    }

    @Transactional
    public String run(String submissionId, int version, String farmId, String reportType, JsonNode payload) {
        String now = Instant.now().toString();
        List<CheckResult> results = new ArrayList<>();
        Map<String, Object> submission = submissions.findHeader(submissionId);
        String actorId = CurrentUserContext.get();
        String actorRole = CurrentUserContext.getRole();
        boolean permittedActor = actorId != null && !actorId.isBlank()
                && actorRole != null && Set.of("WORKER", "ADMINISTRATOR", "MANAGER").contains(actorRole)
                && submission != null && farmId.equals(submission.get("farmId"));
        if (permittedActor) {
            results.add(passed("REPORT_PERMISSION_VALID",
                    "The authenticated actor has a permitted role and the submission belongs to the active farm."));
        } else {
            results.add(blocked("REPORT_PERMISSION_INVALID",
                    "The report could not be checked under an authorized farm account.",
                    Map.of("role", actorRole == null ? "UNKNOWN" : actorRole)));
        }

        JsonNode persistedPayload = submissions.findVersions(submissionId).stream()
                .filter(row -> row.get("version") instanceof Number storedVersion
                        && storedVersion.intValue() == version)
                .map(row -> (JsonNode) row.get("payload"))
                .findFirst().orElse(null);
        boolean versionIntegrity = submission != null
                && reportType.equals(submission.get("reportType"))
                && submission.get("currentVersion") instanceof Number current
                && current.intValue() == version
                && submission.get("submittedVersion") instanceof Number submitted
                && submitted.intValue() == version
                && payload != null && payload.isObject()
                && persistedPayload != null && persistedPayload.equals(payload);
        if (versionIntegrity) {
            results.add(passed("REPORT_VERSION_INTEGRITY",
                    "The immutable submitted version, report type and payload match the review request."));
        } else {
            results.add(blocked("REPORT_VERSION_INTEGRITY",
                    "The submitted version or report payload no longer matches the version being checked.",
                    Map.of("requestedVersion", version, "reportType", reportType)));
        }

        Class<?> dtoType = dtoType(reportType);
        if (dtoType == null) {
            results.add(blocked("UNSUPPORTED_REPORT_TYPE", "This report type is not supported for official records.", Map.of("reportType", reportType)));
        } else {
            List<String> violations = validatePayload(dtoType, payload, reportType);
            if (!violations.isEmpty()) {
                results.add(blocked("REPORT_DATA_VALIDATION", String.join(" ", violations), Map.of("violations", violations)));
            } else {
                results.add(passed("REPORT_DATA_VALIDATION", "Required fields and value constraints passed."));
            }
        }

        String batchId = text(payload, "batchId");
        LocalDate eventDate = parseDate(firstText(payload, "eventDate", "recordDate", "usageDate", "occurredDate"));
        var farm = farms.findActive();
        String timezone = farm == null ? "UTC" : farm.timezone();
        BatchResponse batch = null;
        boolean batchRequired = !"EXPENSE".equals(reportType);
        if (batchId == null || batchId.isBlank()) {
            if (batchRequired) results.add(blocked("BATCH_REQUIRED", "Select the batch or flock this report belongs to.", Map.of()));
        } else {
            batch = batches.findById(batchId);
            if (batch == null || !farmId.equals(batch.farmId()) || !"ACTIVE".equals(batch.status())) {
                results.add(blocked("BATCH_NOT_AVAILABLE", "The selected batch is not active on this farm.", Map.of("batchId", batchId)));
                batch = null;
            } else if (!batchTypeAllowed(reportType, batch.type())) {
                results.add(blocked("BATCH_TYPE_MISMATCH", "This report type is not valid for the selected batch type.",
                        Map.of("batchType", batch.type(), "reportType", reportType)));
            } else {
                results.add(passed("BATCH_REFERENCE_VALID", "The selected active batch belongs to this farm."));
                if (eventDate != null && eventDate.isBefore(batch.placementDate())) {
                    results.add(blocked("EVENT_BEFORE_BATCH_PLACEMENT",
                            "The event date is earlier than the batch placement date.",
                            Map.of("placementDate", batch.placementDate().toString())));
                }
            }
        }

        if ("FEED_USAGE".equals(reportType) && batch != null) {
            String feedTypeId = text(payload, "feedTypeId");
            var feed = feedTypeId == null ? null : feeds.findType(feedTypeId);
            if (feed == null || !farmId.equals(feeds.typeFarmId(feedTypeId)) || !"ACTIVE".equals(feed.status())) {
                results.add(blocked("FEED_TYPE_NOT_AVAILABLE", "Select an active feed type from this farm.", Map.of()));
            } else if (!feed.applicableTypes().contains(batch.type())) {
                results.add(blocked("FEED_NOT_APPLICABLE", "This feed type is not configured for the selected batch type.",
                        Map.of("feedTypeId", feedTypeId, "batchType", batch.type())));
            } else if (!feed.unit().equalsIgnoreCase(text(payload, "unit") == null ? "" : text(payload, "unit"))) {
                results.add(blocked("FEED_UNIT_MISMATCH", "The report unit does not match the selected feed type unit.",
                        Map.of("expectedUnit", feed.unit())));
            } else {
                results.add(passed("FEED_REFERENCE_VALID", "The feed type, applicability and unit are valid."));
            }
        }

        if (batch != null && eventDate != null
                && Set.of("MORTALITY", "CULLING", "BIRD_SALE").contains(reportType)) {
            int quantity = integer(payload, "quantity");
            int available = population.get(batchId, eventDate).currentBirds();
            if (quantity > available) {
                results.add(blocked("POPULATION_EXCEEDED",
                        "The reported quantity exceeds the known live population on the event date.",
                        Map.of("reportedQuantity", quantity, "knownLivePopulation", available)));
            } else {
                results.add(passed("POPULATION_WITHIN_LIMIT", "The reported quantity does not exceed the known live population."));
                if ("MORTALITY".equals(reportType)) {
                    checkMortalityThresholds(results, farmId, submissionId, batch, quantity, available, now);
                }
            }
        }

        if (eventDate != null) {
            String eventTime = text(payload, "eventTime");
            if (eventTime == null || eventTime.isBlank()) {
                results.add(blocked("EVENT_TIME_REQUIRED", "Enter the actual time the event or observation occurred.", Map.of()));
            } else {
                try {
                    LocalDateTime localEvent = LocalDateTime.parse(eventDate + "T" + eventTime);
                    Instant eventInstant = localEvent.atZone(ZoneId.of(timezone)).toInstant();
                    Instant receivedAt = Instant.now();
                    if (eventInstant.isAfter(receivedAt.plus(Duration.ofMinutes(5)))) {
                        results.add(blocked("FUTURE_EVENT_TIME", "An event time cannot be in the future.", Map.of()));
                    } else if (Duration.between(eventInstant, receivedAt).compareTo(Duration.ofHours(24)) > 0) {
                        String lateReason = text(payload, "lateReason");
                        if (lateReason == null || lateReason.isBlank()) {
                            results.add(blocked("LATE_REASON_REQUIRED",
                                    "This report is more than 24 hours late. Add a reason before submitting.", Map.of()));
                        } else {
                            results.add(warning("LATE_REPORT_REQUIRES_MANAGER_AUTHORIZATION",
                                    "This report is more than 24 hours late and requires explicit Manager authorization.",
                                    true, Map.of("eventAt", localEvent.toString())));
                        }
                    } else {
                        results.add(passed("EVENT_TIME_VALID", "The event time is within the allowed reporting window."));
                    }
                } catch (Exception exception) {
                    results.add(blocked("INVALID_EVENT_TIME", "Enter a valid event date and time using the farm timezone.", Map.of()));
                }
            }
        } else {
            results.add(blocked("EVENT_DATE_REQUIRED", "Enter the date the event or observation occurred.", Map.of()));
        }

        if (eventDate != null && batch != null) {
            List<String> duplicates = submissions.findPotentialDuplicates(farmId, submissionId,
                    reportType, batchId, eventDate.toString());
            if (!duplicates.isEmpty()) {
                results.add(warning("POSSIBLE_DUPLICATE",
                        "Another submission of this report type exists for the same batch and business date. Compare both versions before merging.",
                        true, Map.of("possibleSubmissionIds", duplicates, "batchId", batchId,
                                "eventDate", eventDate.toString(), "reportType", reportType)));
            }
        }

        if (eventDate != null && batch != null && "DAILY_OPERATIONS".equals(reportType)) {
            Map<String, Object> existing = conflicts.findDailyRecord(batchId, eventDate.toString());
            if (existing != null) {
                results.add(warning("OFFICIAL_DAILY_RECORD_CONFLICT",
                        "An official daily record already exists for this batch and date. Merging may update its notes or water entries; inspect the current record first.",
                        true, existing));
            }
        }

        if (eventDate != null && "EXPENSE".equals(reportType)) {
            JsonNode amount = payload.get("amountMinor");
            String category = text(payload, "category");
            String description = text(payload, "description");
            if (amount != null && amount.canConvertToLong() && category != null && description != null) {
                List<Map<String, Object>> duplicates = conflicts.findExactExpenseDuplicates(
                        farmId, batchId, eventDate.toString(), amount.asLong(), category, description);
                if (!duplicates.isEmpty()) {
                    results.add(warning("OFFICIAL_EXPENSE_DUPLICATE",
                            "An official expense with the same date, batch, category, amount and description already exists. Verify the receipt or source document before merging.",
                            true, Map.of("possibleOfficialRecords", duplicates)));
                }
            }
        }

        if (!submissions.findChecks(submissionId, version).isEmpty()) {
            results.add(passed("CURRENT_DATA_REVALIDATION",
                    "The submitted version was checked again against current farm, batch, feed, population and official-record data."));
        }

        String outcome = results.stream().anyMatch(result -> "BLOCKED".equals(result.outcome())) ? "BLOCKED"
                : results.stream().anyMatch(result -> "WARNING".equals(result.outcome())) ? "FLAGGED" : "PENDING_REVIEW";
        String runId = UUID.randomUUID().toString();
        submissions.insertCheckRun(runId, submissionId, version, outcome, now);
        for (CheckResult result : results) {
            submissions.insertCheck(UUID.randomUUID().toString(), runId, submissionId, version,
                    result.code(), result.outcome(), result.overrideable(), result.explanation(),
                    json(result.details()), now);
        }
        return outcome;
    }

    private void checkMortalityThresholds(List<CheckResult> results, String farmId, String submissionId,
                                          BatchResponse batch, int quantity, int livePopulation, String now) {
        Map<String, Object> count = reporting.thresholdFor(farmId, "UNUSUAL_MORTALITY_COUNT",
                batch.id(), batch.houseId(), batch.type());
        if (count != null && quantity >= ((Number) count.get("value")).doubleValue()) {
            double threshold = ((Number) count.get("value")).doubleValue();
            results.add(warning("UNUSUAL_MORTALITY_COUNT_THRESHOLD",
                    "Reported mortality meets or exceeds the farm-configured count threshold.",
                    true, Map.of("thresholdId", count.get("id"), "threshold", threshold,
                            "unit", count.get("unit"), "quantity", quantity)));
            reporting.notifyManagers(farmId, submissionId, "Unusual mortality threshold exceeded",
                    "Batch " + batch.code() + " reported " + quantity + " deaths; configured threshold is " + threshold + " birds.", now);
        }
        Map<String, Object> percent = reporting.thresholdFor(farmId, "UNUSUAL_MORTALITY_PERCENT",
                batch.id(), batch.houseId(), batch.type());
        double rate = livePopulation <= 0 ? 0 : quantity * 100.0 / livePopulation;
        if (percent != null && rate >= ((Number) percent.get("value")).doubleValue()) {
            double threshold = ((Number) percent.get("value")).doubleValue();
            results.add(warning("UNUSUAL_MORTALITY_PERCENT_THRESHOLD",
                    "Reported mortality meets or exceeds the farm-configured population percentage threshold.",
                    true, Map.of("thresholdId", percent.get("id"), "threshold", threshold,
                            "unit", percent.get("unit"), "percentage", rate)));
            reporting.notifyManagers(farmId, submissionId, "Unusual mortality percentage threshold exceeded",
                    "Batch " + batch.code() + " reported " + String.format(java.util.Locale.ROOT, "%.2f", rate)
                            + "% mortality; configured threshold is " + threshold + "%.", now);
        }
    }

    private Class<?> dtoType(String type) {
        return switch (type) {
            case "DAILY_OPERATIONS" -> DailyRecordRequest.class;
            case "MORTALITY", "CULLING" -> BirdPopulationEventRequest.class;
            case "HEALTH_RECORD" -> HealthRecordRequest.class;
            case "DRUG_TREATMENT" -> DrugRecordRequest.class;
            case "VACCINATION" -> VaccinationRecordRequest.class;
            case "EGG_COLLECTION" -> EggCollectionRequest.class;
            case "WEIGHT_RECORD" -> WeightRecordRequest.class;
            case "FEED_USAGE" -> FeedUsageRequest.class;
            case "EXPENSE" -> ExpenseRequest.class;
            case "BIRD_SALE" -> BirdSaleRequest.class;
            case "EGG_SALE" -> EggSaleRequest.class;
            default -> null;
        };
    }

    private List<String> validatePayload(Class<?> dtoType, JsonNode payload, String reportType) {
        try {
            ObjectNode dtoPayload = payload.deepCopy();
            String dateField = switch (reportType) {
                case "DAILY_OPERATIONS", "HEALTH_RECORD", "DRUG_TREATMENT", "VACCINATION",
                     "EGG_COLLECTION", "WEIGHT_RECORD", "BIRD_SALE", "EGG_SALE" -> "recordDate";
                case "FEED_USAGE" -> "usageDate";
                case "EXPENSE" -> "occurredDate";
                default -> "eventDate";
            };
            if (!dtoPayload.has(dateField) && dtoPayload.has("eventDate")) {
                dtoPayload.set(dateField, dtoPayload.get("eventDate"));
            }
            Object dto = mapper.readerFor(dtoType)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(dtoPayload.traverse(mapper));
            return validator.validate(dto).stream()
                    .map(ConstraintViolation::getMessage)
                    .distinct().sorted().toList();
        } catch (Exception exception) {
            return List.of("The report fields do not match the selected report type.");
        }
    }

    private boolean batchTypeAllowed(String reportType, String batchType) {
        return switch (reportType) {
            case "EGG_COLLECTION", "EGG_SALE" -> "LAYER".equals(batchType);
            case "WEIGHT_RECORD", "BIRD_SALE" -> Set.of("BROILER", "TURKEY").contains(batchType);
            default -> true;
        };
    }

    private String text(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText();
        return text.isBlank() ? null : text.trim();
    }

    private String firstText(JsonNode payload, String... fields) {
        for (String field : fields) {
            String value = text(payload, field);
            if (value != null) return value;
        }
        return null;
    }

    private LocalDate parseDate(String value) {
        if (value == null) return null;
        try { return LocalDate.parse(value); }
        catch (Exception exception) { return null; }
    }

    private int integer(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || !value.canConvertToInt() ? 0 : value.asInt();
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { return "{}"; }
    }

    private CheckResult passed(String code, String explanation) {
        return new CheckResult(code, "PASSED", false, explanation, Map.of());
    }

    private CheckResult blocked(String code, String explanation, Map<String, Object> details) {
        return new CheckResult(code, "BLOCKED", false, explanation, details);
    }

    private CheckResult warning(String code, String explanation, boolean overrideable, Map<String, Object> details) {
        return new CheckResult(code, "WARNING", overrideable, explanation, details);
    }

    private record CheckResult(String code, String outcome, boolean overrideable,
                               String explanation, Map<String, Object> details) {}
}
