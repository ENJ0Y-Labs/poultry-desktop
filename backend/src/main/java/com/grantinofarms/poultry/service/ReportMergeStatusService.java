package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.ReportSubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class ReportMergeStatusService {
    private final ReportSubmissionRepository submissions;
    private final AuditRepository audit;

    public ReportMergeStatusService(ReportSubmissionRepository submissions, AuditRepository audit) {
        this.submissions = submissions;
        this.audit = audit;
    }

    @Transactional
    public void returnToReview(String id, int version, String nextStatus) {
        Map<String, Object> header = submissions.findHeader(id);
        if (header == null || submissions.reopenApprovedAfterRecheck(id, version, nextStatus, Instant.now().toString()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_CHANGED",
                    "The report changed while merge checks were running. Refresh the review centre.");
        }
        String now = Instant.now().toString();
        submissions.appendEvent(id, version, CurrentUserContext.get(), "MERGE_RECHECK_FAILED",
                "{\"lifecycleStatus\":\"" + nextStatus + "\"}", now);
        audit.append(String.valueOf(header.get("farmId")), "RECHECK", "REPORT_SUBMISSION", id,
                "Merge revalidation returned the report to review", null,
                "{\"lifecycleStatus\":\"" + nextStatus + "\",\"version\":" + version + "}", now);
    }
}
