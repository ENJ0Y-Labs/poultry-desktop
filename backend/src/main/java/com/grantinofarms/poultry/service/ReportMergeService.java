package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.ReportSubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ReportMergeService {
    private final ReportSubmissionRepository submissions;
    private final ReportCheckService checks;
    private final ReportMergeStatusService mergeStatus;
    private final ReportMergeTransactionService mergeTransaction;

    public ReportMergeService(ReportSubmissionRepository submissions, ReportCheckService checks,
                              ReportMergeStatusService mergeStatus,
                              ReportMergeTransactionService mergeTransaction) {
        this.submissions = submissions;
        this.checks = checks;
        this.mergeStatus = mergeStatus;
        this.mergeTransaction = mergeTransaction;
    }

    public Map<String, Object> merge(String id, String operationKey) {
        RoleAuthorization.requireManager();
        if (operationKey == null || !operationKey.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid Idempotency-Key header is required for merging.");
        }
        Map<String, Object> header = submissions.findHeader(id);
        if (header == null) throw new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND",
                "Report submission was not found.");
        if ("MERGED".equals(header.get("lifecycleStatus"))) return mergeTransaction.merge(id, operationKey);
        if (CurrentUserContext.get().equals(header.get("authorId"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SELF_MERGE_FORBIDDEN",
                    "A Manager cannot merge their own submission.");
        }
        if (!"APPROVED".equals(header.get("lifecycleStatus"))) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_NOT_APPROVED",
                    "Only an approved report version can be merged.");
        }
        int version = ((Number) header.get("submittedVersion")).intValue();
        List<Map<String, Object>> versions = submissions.findVersions(id);
        JsonNode payload = versions.stream()
                .filter(row -> ((Number) row.get("version")).intValue() == version)
                .map(row -> (JsonNode) row.get("payload"))
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "SUBMISSION_VERSION_NOT_FOUND", "The approved report version was not found."));
        String recheckStatus = checks.run(id, version, String.valueOf(header.get("farmId")),
                String.valueOf(header.get("reportType")), payload);
        if (!"PENDING_REVIEW".equals(recheckStatus)) {
            mergeStatus.returnToReview(id, version, recheckStatus);
            throw new ApiException(HttpStatus.CONFLICT, "MERGE_RECHECK_FAILED",
                    "The latest farm data changed the validation result. The report has been returned to review.");
        }
        return mergeTransaction.merge(id, operationKey);
    }
}
