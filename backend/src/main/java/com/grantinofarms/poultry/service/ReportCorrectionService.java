package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.grantinofarms.poultry.dto.WeightRecordCorrectionRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.ReportSubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportCorrectionService {
    private final ReportSubmissionRepository submissions;
    private final BroilerProductionService broiler;
    private final AuditRepository audit;

    public ReportCorrectionService(ReportSubmissionRepository submissions,
                                   BroilerProductionService broiler,
                                   AuditRepository audit) {
        this.submissions = submissions;
        this.broiler = broiler;
        this.audit = audit;
    }

    /**
     * First typed post-merge correction adapter. Other report types must not be
     * routed through a generic SQL dispatcher; they need their own domain rules.
     */
    @Transactional
    public Map<String, Object> correctWeight(String submissionId, WeightRecordCorrectionRequest request) {
        RoleAuthorization.requireManager();
        Map<String, Object> header = submissions.findHeader(submissionId);
        if (header == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND",
                    "Report submission was not found.");
        }
        if (!"MERGED".equals(header.get("lifecycleStatus"))) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_NOT_MERGED",
                    "Only a merged report can be corrected after merge.");
        }
        if (!"WEIGHT_RECORD".equals(header.get("reportType"))) {
            throw new ApiException(HttpStatus.CONFLICT, "CORRECTION_NOT_SUPPORTED",
                    "This report type does not yet have a safe post-merge correction adapter.");
        }
        String actor = CurrentUserContext.get();
        if (actor.equals(header.get("authorId"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SELF_CORRECTION_FORBIDDEN",
                    "A Manager cannot correct their own merged submission.");
        }

        int version = ((Number) header.get("submittedVersion")).intValue();
        JsonNode payload = submissions.findVersions(submissionId).stream()
                .filter(row -> ((Number) row.get("version")).intValue() == version)
                .map(row -> (JsonNode) row.get("payload"))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_VERSION_NOT_FOUND",
                        "The merged report version could not be found."));
        String batchId = payload.path("batchId").asText("");
        if (batchId.isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "CORRECTION_BATCH_MISSING",
                    "The original weight report has no batch reference.");
        }

        List<Map<String, Object>> originalLinks = submissions.findLinks(submissionId).stream()
                .filter(link -> ((Number) link.get("version")).intValue() == version)
                .filter(link -> "WEIGHT_RECORD".equals(link.get("recordType")))
                .toList();
        if (originalLinks.size() != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "CORRECTION_PROVENANCE_INVALID",
                    "The merged report does not have exactly one original weight record link.");
        }
        String originalId = String.valueOf(originalLinks.get(0).get("recordId"));
        String reason = request == null ? null : request.reason();
        var replacement = request == null ? null : request.replacement();
        Map<String, Object> corrected = broiler.correctWeight(batchId, originalId, replacement, reason);
        String replacementId = String.valueOf(corrected.get("replacementId"));
        String now = Instant.now().toString();

        submissions.insertLink(submissionId, version, "WEIGHT_RECORD", replacementId, now);
        String details = "{\\"originalRecordId\\":\\"" + escape(originalId)
                + "\\",\\"replacementRecordId\\":\\"" + escape(replacementId)
                + "\\",\\"reason\\":\\"" + escape(reason.trim()) + "\\"}";
        submissions.appendEvent(submissionId, version, actor, "POST_MERGE_CORRECTED", details, now);
        audit.append(String.valueOf(header.get("farmId")), "POST_MERGE_CORRECT",
                "REPORT_SUBMISSION", submissionId,
                "Merged weight record corrected through a typed reversal and replacement",
                "{\\"originalRecordId\\":\\"" + escape(originalId) + "\\"}",
                "{\\"replacementRecordId\\":\\"" + escape(replacementId) + "\\"}", now);

        Map<String, Object> result = new LinkedHashMap<>(corrected);
        result.put("submissionId", submissionId);
        result.put("version", version);
        return result;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\\"", "\\\\"")
                .replace("\\n", " ").replace("\\r", " ");
    }
}
