package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.dto.ReportSubmissionRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.ReportSubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportSubmissionService {
    private final ReportSubmissionRepository submissions;
    private final FarmRepository farms;
    private final AuditRepository audit;
    private final ObjectMapper mapper;
    private final ReportCheckService checks;

    public ReportSubmissionService(ReportSubmissionRepository submissions, FarmRepository farms,
                                   AuditRepository audit, ObjectMapper mapper, ReportCheckService checks) {
        this.submissions = submissions;
        this.farms = farms;
        this.audit = audit;
        this.mapper = mapper;
        this.checks = checks;
    }

    @Transactional
    public Map<String, Object> createDraft(ReportSubmissionRequest request) {
        return createDraft(request, UUID.randomUUID().toString());
    }

    public Map<String, Object> createDraft(ReportSubmissionRequest request, String idempotencyKey) {
        RoleAuthorization.requireReportSubmitter();
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid Idempotency-Key header is required.");
        }
        String actorId = CurrentUserContext.get();
        var farm = farms.findActive();
        if (farm == null) throw new ApiException(HttpStatus.CONFLICT, "FARM_NOT_CONFIGURED",
                "Create or activate a farm before saving reports.");
        validateRequest(request);
        String reportType = request.reportType().trim().toUpperCase();
        String reportTitle = request.title().trim();
        String payloadJson = json(request.payload());
        String requestHash = requestHash(reportType, reportTitle, payloadJson);
        Map<String, Object> existing = submissions.findByIdempotencyKey(farm.id(), actorId, idempotencyKey);
        if (existing != null) {
            requireSameIdempotentRequest(existing, requestHash);
            return getOwned(String.valueOf(existing.get("id")), actorId);
        }
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        try {
            submissions.insert(id, farm.id(), actorId, reportType, reportTitle,
                    payloadJson, idempotencyKey, requestHash, now);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            Map<String, Object> raced = submissions.findByIdempotencyKey(farm.id(), actorId, idempotencyKey);
            if (raced != null) {
                requireSameIdempotentRequest(raced, requestHash);
                return getOwned(String.valueOf(raced.get("id")), actorId);
            }
            throw ex;
        }
        audit.append(farm.id(), "CREATE", "REPORT_SUBMISSION", id, "Report draft created",
                null, "{\"status\":\"DRAFT\",\"version\":1}", now);
        return getOwned(id, actorId);
    }

    @Transactional
    public Map<String, Object> saveDraft(String id, ReportSubmissionRequest request) {
        RoleAuthorization.requireReportSubmitter();
        String actorId = CurrentUserContext.get();
        Map<String, Object> header = requireOwnedHeader(id, actorId);
        String status = String.valueOf(header.get("lifecycleStatus"));
        if (!"DRAFT".equals(status) && !"CHANGES_REQUESTED".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_NOT_EDITABLE",
                    "Only a draft or a report returned for changes can be edited.");
        }
        validateRequest(request);
        int nextVersion = ((Number) header.get("currentVersion")).intValue() + 1;
        String now = Instant.now().toString();
        int previousVersion = nextVersion - 1;
        int updated = submissions.updateDraft(id, actorId, request.reportType().trim().toUpperCase(),
                request.title().trim(), previousVersion, nextVersion, now);
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_CHANGED",
                "The report changed while it was being saved. Reload it and try again.");
        submissions.insertVersion(id, nextVersion, request.reportType().trim().toUpperCase(),
                request.title().trim(), json(request.payload()), actorId, now);
        submissions.appendEvent(id, nextVersion, actorId, "DRAFT_VERSION_SAVED",
                "{\"previousVersion\":" + (nextVersion - 1) + "}", now);
        audit.append((String) header.get("farmId"), "UPDATE", "REPORT_SUBMISSION", id,
                "Report draft version saved", "{\"version\":" + (nextVersion - 1) + "}",
                "{\"version\":" + nextVersion + "}", now);
        return getOwned(id, actorId);
    }

    @Transactional
    public Map<String, Object> submit(String id) {
        RoleAuthorization.requireReportSubmitter();
        String actorId = CurrentUserContext.get();
        Map<String, Object> header = requireOwnedHeader(id, actorId);
        int version = ((Number) header.get("currentVersion")).intValue();
        String now = Instant.now().toString();
        if (submissions.submit(id, actorId, version, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_NOT_SUBMITTABLE",
                    "Only the current draft version can be submitted.");
        }
        submissions.appendEvent(id, version, actorId, "SUBMITTED", null, now);
        List<Map<String, Object>> versions = submissions.findVersions(id);
        JsonNode payload = (JsonNode) versions.get(versions.size() - 1).get("payload");
        String nextStatus = checks.run(id, version, String.valueOf(header.get("farmId")),
                String.valueOf(header.get("reportType")), payload);
        if (submissions.updateLifecycleStatus(id, version, "SUBMITTED", nextStatus, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_CHANGED",
                    "The report changed while its checks were running. Reload and submit again.");
        }
        submissions.appendEvent(id, version, actorId, "CHECKS_COMPLETED",
                "{\"lifecycleStatus\":\"" + nextStatus + "\"}", now);
        audit.append((String) header.get("farmId"), "SUBMIT", "REPORT_SUBMISSION", id,
                "Report submitted and automated checks completed", null,
                "{\"lifecycleStatus\":\"" + nextStatus + "\",\"version\":" + version + "}", now);
        return getOwned(id, actorId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine() {
        RoleAuthorization.requireReportSubmitter();
        return submissions.findMine(CurrentUserContext.get());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String id) {
        RoleAuthorization.requireReportSubmitter();
        return getOwned(id, CurrentUserContext.get());
    }

    private Map<String, Object> getOwned(String id, String actorId) {
        Map<String, Object> header = requireOwnedHeader(id, actorId);
        Map<String, Object> result = new LinkedHashMap<>(header);
        result.put("versions", submissions.findVersions(id));
        result.put("history", submissions.findEvents(id));
        result.put("comments", submissions.findComments(id));
        result.put("checks", submissions.findChecks(id, ((Number) header.get("currentVersion")).intValue()));
        result.put("decisions", submissions.findDecisions(id));
        result.put("assignments", submissions.findAssignments(id));
        return result;
    }

    private Map<String, Object> requireOwnedHeader(String id, String actorId) {
        Map<String, Object> header = submissions.findHeader(id);
        if (header == null || !actorId.equals(header.get("authorId"))) {
            // Do not disclose whether another user's identifier exists.
            throw new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "Report submission was not found.");
        }
        return header;
    }

    private void validateRequest(ReportSubmissionRequest request) {
        if (request.payload() == null || !request.payload().isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_PAYLOAD",
                    "Report payload must be a JSON object.");
        }
        if (request.reportType() == null || request.reportType().isBlank()
                || request.reportType().length() > 80
                || request.title() == null || request.title().isBlank() || request.title().length() > 160) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT",
                    "Report type and title are required.");
        }
    }

    private void requireSameIdempotentRequest(Map<String, Object> existing, String requestHash) {
        if (!requestHash.equals(existing.get("idempotencyPayloadHash"))) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used with different report content.");
        }
    }

    private String requestHash(String reportType, String title, String payloadJson) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((reportType + "\\n" + title + "\\n" + payloadJson)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private String json(JsonNode payload) {
        try {
            String serialized = mapper.writeValueAsString(payload);
            if (serialized.length() > 1_000_000) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_PAYLOAD_TOO_LARGE",
                        "Report data must be smaller than 1 MB.");
            }
            return serialized;
        }
        catch (ApiException e) {
            throw e;
        }
        catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_PAYLOAD",
                    "Report data could not be serialized.");
        }
    }
}
