package com.grantinofarms.poultry.service;

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
public class ReportReviewService {
    private final ReportSubmissionRepository submissions;
    private final AuditRepository audit;

    public ReportReviewService(ReportSubmissionRepository submissions, AuditRepository audit) {
        this.submissions = submissions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> queue(String status, String reportType, String authorId) {
        RoleAuthorization.requireManager();
        return submissions.findReviewQueue(cleanFilter(status), cleanFilter(reportType), cleanFilter(authorId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> managers() {
        RoleAuthorization.requireManager();
        return submissions.findActiveManagers();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String id) {
        RoleAuthorization.requireManager();
        return detail(requireHeader(id));
    }

    @Transactional
    public Map<String, Object> assign(String id, String managerId) {
        RoleAuthorization.requireManager();
        String actor = CurrentUserContext.get();
        if (managerId == null || managerId.isBlank() || !submissions.isActiveManager(managerId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVIEWER",
                    "Choose an active Manager account.");
        }
        Map<String, Object> header = requireHeader(id);
        String status = String.valueOf(header.get("lifecycleStatus"));
        if (!List.of("SUBMITTED", "PENDING_REVIEW", "FLAGGED", "BLOCKED").contains(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_NOT_ASSIGNABLE",
                    "Only reports awaiting review can be assigned.");
        }
        String now = Instant.now().toString();
        if (submissions.assign(id, managerId, actor, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_CHANGED",
                    "The report changed before assignment. Refresh the review queue.");
        }
        submissions.appendEvent(id, version(header), actor, "REVIEW_ASSIGNED",
                "{\"managerId\":\"" + managerId + "\"}", now);
        audit.append(String.valueOf(header.get("farmId")), "ASSIGN", "REPORT_SUBMISSION", id,
                "Review assigned to Manager", null, "{\"managerId\":\"" + managerId + "\"}", now);
        return detail(requireHeader(id));
    }

    @Transactional
    public Map<String, Object> comment(String id, String body) {
        String actor = RoleAuthorization.requireAuthenticated();
        Map<String, Object> header = requireHeader(id);
        boolean manager = "MANAGER".equals(CurrentUserContext.getRole());
        if (!manager && !actor.equals(header.get("authorId"))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND",
                    "Report submission was not found.");
        }
        if (body == null || body.isBlank() || body.trim().length() > 4000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COMMENT",
                    "Comment must contain 1 to 4,000 characters.");
        }
        int version = version(header);
        String now = Instant.now().toString();
        submissions.addComment(id, version, actor, body.trim(), now);
        submissions.appendEvent(id, version, actor, "COMMENT_ADDED", null, now);
        audit.append(String.valueOf(header.get("farmId")), "COMMENT", "REPORT_SUBMISSION", id,
                "Report discussion comment added", null,
                "{\"version\":" + version + "}", now);
        return detail(requireHeader(id));
    }

    @Transactional
    public Map<String, Object> overrideWarning(String id, String checkId, String reason) {
        RoleAuthorization.requireManager();
        String actor = CurrentUserContext.get();
        Map<String, Object> header = requireHeader(id);
        if (actor.equals(header.get("authorId"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SELF_REVIEW_FORBIDDEN",
                    "A Manager cannot authorize warnings on their own submission.");
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isBlank() || cleanReason.length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OVERRIDE_REASON_REQUIRED",
                    "A reason of 1 to 1,000 characters is required.");
        }
        int version = submittedVersion(header);
        Map<String, Object> check = submissions.findCheck(id, version, checkId);
        if (check == null || !"WARNING".equals(check.get("outcome"))
                || !Boolean.TRUE.equals(check.get("overrideable"))) {
            throw new ApiException(HttpStatus.CONFLICT, "WARNING_NOT_OVERRIDEABLE",
                    "Only a warning explicitly marked as overrideable can be authorized.");
        }
        if (submissions.hasWarningOverride(id, version, checkId)) {
            throw new ApiException(HttpStatus.CONFLICT, "WARNING_ALREADY_OVERRIDDEN",
                    "This warning already has an authorization record.");
        }
        String now = Instant.now().toString();
        submissions.addWarningOverride(id, version, checkId, actor, cleanReason, now);
        submissions.appendEvent(id, version, actor, "WARNING_OVERRIDDEN",
                "{\\"checkId\\":\\"" + checkId + "\\",\\"reason\\":\\"" + escape(cleanReason) + "\\"}", now);
        audit.append(String.valueOf(header.get("farmId")), "OVERRIDE_WARNING", "REPORT_SUBMISSION", id,
                "Manager authorized an overrideable report warning", null,
                "{\\"checkId\\":\\"" + checkId + "\\",\\"version\\":" + version + "}", now);
        return detail(requireHeader(id));
    }

    @Transactional
    public Map<String, Object> decide(String id, String decision, String reason) {
        RoleAuthorization.requireManager();
        String actor = CurrentUserContext.get();
        Map<String, Object> header = requireHeader(id);
        if (actor.equals(header.get("authorId"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SELF_REVIEW_FORBIDDEN",
                    "A Manager cannot review or decide on their own submission.");
        }
        String normalized = decision == null ? "" : decision.trim().toUpperCase();
        if (!List.of("CHANGES_REQUESTED", "REJECTED", "APPROVED").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_DECISION",
                    "Choose request changes, reject, or approve.");
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (!"APPROVED".equals(normalized) && (cleanReason.isBlank() || cleanReason.length() > 2000)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REVIEW_REASON_REQUIRED",
                    "A reason of 1 to 2,000 characters is required for this decision.");
        }
        int version = submittedVersion(header);
        String status = String.valueOf(header.get("lifecycleStatus"));
        if (!List.of("SUBMITTED", "PENDING_REVIEW", "FLAGGED", "BLOCKED").contains(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_NOT_REVIEWABLE",
                    "This report is not awaiting a review decision.");
        }
        if ("APPROVED".equals(normalized)) {
            List<Map<String, Object>> checks = submissions.findChecks(id, version);
            boolean blocked = checks.stream().anyMatch(check -> "BLOCKED".equals(check.get("outcome")));
            if (blocked) {
                throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_BLOCKED",
                        "A blocked report cannot be approved. The author must correct the report first.");
            }
            boolean unresolvedWarning = checks.stream()
                    .filter(check -> "WARNING".equals(check.get("outcome")))
                    .anyMatch(check -> !Boolean.TRUE.equals(check.get("overrideable"))
                            || !submissions.hasWarningOverride(id, version, String.valueOf(check.get("id"))));
            if (unresolvedWarning) {
                throw new ApiException(HttpStatus.CONFLICT, "WARNING_OVERRIDE_REQUIRED",
                        "Every overrideable warning requires a separate Manager authorization with a reason.");
            }
        }
        String now = Instant.now().toString();
        // Insert and transition share one transaction. A stale version or competing decision rolls both back.
        submissions.addDecision(id, version, actor, normalized,
                "APPROVED".equals(normalized) ? null : cleanReason, now);
        if (submissions.transition(id, version, status, normalized, actor, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_CHANGED",
                    "Another Manager changed this report. Refresh before deciding.");
        }
        String event = switch (normalized) {
            case "CHANGES_REQUESTED" -> "CHANGES_REQUESTED";
            case "REJECTED" -> "REJECTED";
            default -> "APPROVED";
        };
        submissions.appendEvent(id, version, actor, event,
                "APPROVED".equals(normalized) ? null : "{\"reason\":\"" + escape(cleanReason) + "\"}", now);
        audit.append(String.valueOf(header.get("farmId")), normalized, "REPORT_SUBMISSION", id,
                "Manager review decision", "{\"status\":\"" + status + "\"}",
                "{\"status\":\"" + normalized + "\",\"version\":" + version + "}", now);
        return detail(requireHeader(id));
    }

    private Map<String, Object> detail(Map<String, Object> header) {
        String id = String.valueOf(header.get("id"));
        Map<String, Object> result = new LinkedHashMap<>(header);
        result.put("versions", submissions.findVersions(id));
        result.put("history", submissions.findEvents(id));
        result.put("comments", submissions.findComments(id));
        result.put("checks", submissions.findChecks(id, submittedVersion(header)));
        result.put("decisions", submissions.findDecisions(id));
        result.put("assignments", submissions.findAssignments(id));
        return result;
    }

    private Map<String, Object> requireHeader(String id) {
        Map<String, Object> header = submissions.findHeader(id);
        if (header == null) throw new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND",
                "Report submission was not found.");
        return header;
    }

    private int submittedVersion(Map<String, Object> header) {
        Object value = header.get("submittedVersion");
        return value instanceof Number number ? number.intValue() : version(header);
    }

    private int version(Map<String, Object> header) {
        return ((Number) header.get("currentVersion")).intValue();
    }

    private String cleanFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
