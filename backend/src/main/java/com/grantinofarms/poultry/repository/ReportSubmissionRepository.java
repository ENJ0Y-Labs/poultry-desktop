package com.grantinofarms.poultry.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ReportSubmissionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ReportSubmissionRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void insert(String id, String farmId, String authorId, String reportType, String title,
                       String payloadJson, String now) {
        insert(id, farmId, authorId, reportType, title, payloadJson, null, now);
    }

    public void insert(String id, String farmId, String authorId, String reportType, String title,
                       String payloadJson, String idempotencyKey, String now) {
        jdbc.update("""
                INSERT INTO report_submissions
                    (id, farm_id, author_user_id, report_type, title, status, lifecycle_status,
                     current_version, idempotency_key, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'DRAFT', 'DRAFT', 1, ?, ?, ?)
                """, id, farmId, authorId, reportType, title, idempotencyKey, now, now);
        insertVersion(id, 1, reportType, title, payloadJson, authorId, now);
        appendEvent(id, 1, authorId, "DRAFT_CREATED", null, now);
    }

    public void insertVersion(String id, int version, String reportType, String title,
                              String payloadJson, String actorId, String now) {
        jdbc.update("""
                INSERT INTO report_submission_versions
                    (submission_id, version_number, report_type, title, payload_json, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, version, reportType, title, payloadJson, actorId, now);
    }

    public int updateDraft(String id, String actorId, String reportType, String title,
                           int expectedVersion, int nextVersion, String now) {
        return jdbc.update("""
                UPDATE report_submissions
                SET report_type = ?, title = ?, current_version = ?, status = 'DRAFT',
                    lifecycle_status = 'DRAFT', updated_at = ?
                WHERE id = ? AND author_user_id = ?
                  AND lifecycle_status IN ('DRAFT','CHANGES_REQUESTED')
                  AND current_version = ?
                """, reportType, title, nextVersion, now, id, actorId, expectedVersion);
    }

    public void appendEvent(String id, Integer version, String actorId, String event,
                            String detailsJson, String now) {
        jdbc.update("""
                INSERT INTO report_submission_events
                    (id, submission_id, version_number, actor_user_id, event_type, details_json, occurred_at)
                VALUES (lower(hex(randomblob(16))), ?, ?, ?, ?, ?, ?)
                """, id, version, actorId, event, detailsJson, now);
    }

    public int submit(String id, String actorId, int version, String now) {
        return jdbc.update("""
                UPDATE report_submissions
                SET status = 'PENDING_REVIEW', lifecycle_status = 'SUBMITTED',
                    submitted_version = ?, submitted_at = ?, updated_at = ?
                WHERE id = ? AND author_user_id = ?
                  AND lifecycle_status IN ('DRAFT','CHANGES_REQUESTED')
                  AND current_version = ?
                """, version, now, now, id, actorId, version);
    }

    public Map<String, Object> findByIdempotencyKey(String farmId, String authorId, String key) {
        if (key == null || key.isBlank()) return null;
        return jdbc.query("""
                SELECT id, farm_id, author_user_id, report_type, title, status, lifecycle_status,
                       current_version, submitted_version, created_at, updated_at, submitted_at
                FROM report_submissions
                WHERE farm_id = ? AND author_user_id = ? AND idempotency_key = ?
                """, rs -> rs.next() ? header(rs) : null, farmId, authorId, key);
    }

    public Map<String, Object> findHeader(String id) {
        return jdbc.query("""
                SELECT id, farm_id, author_user_id, report_type, title, status, lifecycle_status,
                       current_version, submitted_version, created_at, updated_at, submitted_at,
                       assigned_manager_user_id, scope_type, scope_id, event_date, late_reason,
                       decision_version, merged_at
                FROM report_submissions WHERE id = ?
                """, rs -> rs.next() ? header(rs) : null, id);
    }

    public List<Map<String, Object>> findMine(String actorId) {
        return jdbc.query("""
                SELECT id, farm_id, author_user_id, report_type, title, status, lifecycle_status,
                       current_version, submitted_version, created_at, updated_at, submitted_at,
                       assigned_manager_user_id, scope_type, scope_id, event_date, late_reason,
                       decision_version, merged_at
                FROM report_submissions WHERE author_user_id = ?
                ORDER BY updated_at DESC, id
                """, (rs, n) -> header(rs), actorId);
    }

    public List<Map<String, Object>> findReviewQueue(String lifecycleStatus, String reportType, String authorId) {
        return jdbc.query("""
                SELECT s.id, s.farm_id, s.author_user_id, s.report_type, s.title, s.status,
                       s.lifecycle_status, s.current_version, s.submitted_version, s.created_at,
                       s.updated_at, s.submitted_at, s.assigned_manager_user_id, s.scope_type,
                       s.scope_id, s.event_date, s.late_reason, s.decision_version, s.merged_at,
                       u.username AS author_username, u.full_name AS author_full_name
                FROM report_submissions s JOIN users u ON u.id = s.author_user_id
                WHERE (? IS NULL OR s.lifecycle_status = ?)
                  AND (? IS NULL OR s.report_type = ?)
                  AND (? IS NULL OR s.author_user_id = ?)
                  AND s.lifecycle_status NOT IN ('DRAFT','MERGED','REJECTED')
                ORDER BY CASE s.lifecycle_status
                    WHEN 'BLOCKED' THEN 0 WHEN 'FLAGGED' THEN 1
                    WHEN 'SUBMITTED' THEN 2 WHEN 'PENDING_REVIEW' THEN 3 ELSE 4 END,
                    s.submitted_at, s.id
                """, (rs, n) -> header(rs), lifecycleStatus, lifecycleStatus,
                reportType, reportType, authorId, authorId);
    }

    public List<Map<String, Object>> findVersions(String id) {
        return jdbc.query("""
                SELECT version_number, report_type, title, payload_json, created_by, created_at
                FROM report_submission_versions WHERE submission_id = ?
                ORDER BY version_number
                """, (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("version", rs.getInt("version_number"));
            row.put("reportType", rs.getString("report_type"));
            row.put("title", rs.getString("title"));
            try { row.put("payload", mapper.readTree(rs.getString("payload_json"))); }
            catch (Exception e) { throw new IllegalStateException("Stored report payload is invalid.", e); }
            row.put("createdBy", rs.getString("created_by"));
            row.put("createdAt", rs.getString("created_at"));
            return row;
        }, id);
    }

    public List<Map<String, Object>> findEvents(String id) {
        return jdbc.query("""
                SELECT id, version_number, actor_user_id, event_type, details_json, occurred_at
                FROM report_submission_events WHERE submission_id = ?
                ORDER BY occurred_at, id
                """, (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("version", rs.getObject("version_number"));
            row.put("actorId", rs.getString("actor_user_id"));
            row.put("event", rs.getString("event_type"));
            try {
                String details = rs.getString("details_json");
                row.put("details", details == null ? null : mapper.readTree(details));
            } catch (Exception e) { throw new IllegalStateException("Stored submission event is invalid.", e); }
            row.put("occurredAt", rs.getString("occurred_at"));
            return row;
        }, id);
    }

    public List<Map<String, Object>> findComments(String id) {
        return jdbc.query("""
                SELECT c.id, c.version_number, c.author_user_id, u.full_name AS author_name,
                       c.body, c.created_at
                FROM report_submission_comments c
                JOIN users u ON u.id = c.author_user_id
                WHERE c.submission_id = ?
                ORDER BY c.created_at, c.id
                """, (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("version", rs.getInt("version_number"));
            row.put("authorId", rs.getString("author_user_id"));
            row.put("authorName", rs.getString("author_name"));
            row.put("body", rs.getString("body"));
            row.put("createdAt", rs.getString("created_at"));
            return row;
        }, id);
    }

    public List<Map<String, Object>> findChecks(String id, int version) {
        return jdbc.query("""
                SELECT id, check_code, outcome, overrideable, explanation, details_json, checked_at
                FROM report_submission_checks
                WHERE submission_id = ? AND version_number = ?
                ORDER BY checked_at, check_code
                """, (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("code", rs.getString("check_code"));
            row.put("outcome", rs.getString("outcome"));
            row.put("overrideable", rs.getInt("overrideable") == 1);
            row.put("explanation", rs.getString("explanation"));
            try {
                String details = rs.getString("details_json");
                row.put("details", details == null ? null : mapper.readTree(details));
            } catch (Exception e) { throw new IllegalStateException("Stored report check is invalid.", e); }
            row.put("checkedAt", rs.getString("checked_at"));
            return row;
        }, id, version);
    }

    public List<Map<String, Object>> findDecisions(String id) {
        return jdbc.query("""
                SELECT d.id, d.version_number, d.manager_user_id, u.full_name AS manager_name,
                       d.decision, d.reason, d.created_at
                FROM report_submission_decisions d
                JOIN users u ON u.id = d.manager_user_id
                WHERE d.submission_id = ?
                ORDER BY d.created_at, d.id
                """, (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("version", rs.getInt("version_number"));
            row.put("managerId", rs.getString("manager_user_id"));
            row.put("managerName", rs.getString("manager_name"));
            row.put("decision", rs.getString("decision"));
            row.put("reason", rs.getString("reason"));
            row.put("createdAt", rs.getString("created_at"));
            return row;
        }, id);
    }

    public List<Map<String, Object>> findAssignments(String id) {
        return jdbc.query("""
                SELECT a.id, a.manager_user_id, m.full_name AS manager_name,
                       a.assigned_by_user_id, a.created_at
                FROM report_submission_assignments a
                JOIN users m ON m.id = a.manager_user_id
                WHERE a.submission_id = ?
                ORDER BY a.created_at, a.id
                """, (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("managerId", rs.getString("manager_user_id"));
            row.put("managerName", rs.getString("manager_name"));
            row.put("assignedBy", rs.getString("assigned_by_user_id"));
            row.put("createdAt", rs.getString("created_at"));
            return row;
        }, id);
    }

    public int assign(String submissionId, String managerId, String actorId, String now) {
        int updated = jdbc.update("""
                UPDATE report_submissions SET assigned_manager_user_id = ?, updated_at = ?
                WHERE id = ? AND lifecycle_status IN ('SUBMITTED','PENDING_REVIEW','FLAGGED','BLOCKED')
                """, managerId, now, submissionId);
        if (updated == 1) jdbc.update("""
                INSERT INTO report_submission_assignments
                    (id, submission_id, manager_user_id, assigned_by_user_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, java.util.UUID.randomUUID().toString(), submissionId, managerId, actorId, now);
        return updated;
    }

    public boolean isActiveManager(String managerId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM users u JOIN user_roles r ON r.user_id = u.id
                WHERE u.id = ? AND u.status = 'ACTIVE' AND r.role = 'MANAGER'
                """, Integer.class, managerId);
        return count != null && count > 0;
    }

    public void addComment(String id, int version, String actorId, String body, String now) {
        jdbc.update("""
                INSERT INTO report_submission_comments
                    (id, submission_id, version_number, author_user_id, body, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, java.util.UUID.randomUUID().toString(), id, version, actorId, body, now);
    }

    public void addDecision(String id, int version, String managerId, String decision,
                            String reason, String now) {
        jdbc.update("""
                INSERT INTO report_submission_decisions
                    (id, submission_id, version_number, manager_user_id, decision, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, java.util.UUID.randomUUID().toString(), id, version, managerId, decision, reason, now);
    }

    public int transition(String id, int expectedVersion, String expectedStatus,
                          String nextStatus, String actorId, String now) {
        String legacyStatus = switch (nextStatus) {
            case "CHANGES_REQUESTED" -> "CHANGES_REQUESTED";
            case "REJECTED" -> "REJECTED";
            case "APPROVED" -> "APPROVED";
            case "PENDING_REVIEW" -> "PENDING_REVIEW";
            default -> expectedStatus.equals("DRAFT") ? "DRAFT" : "PENDING_REVIEW";
        };
        return jdbc.update("""
                UPDATE report_submissions
                SET lifecycle_status = ?, status = ?, decision_version = ?,
                    updated_at = ?
                WHERE id = ? AND current_version = ? AND lifecycle_status = ?
                """, nextStatus, legacyStatus, expectedVersion, now, id, expectedVersion, expectedStatus);
    }

    public Map<String, Object> findUser(String userId) {
        return jdbc.query("""
                SELECT id, status, role FROM users WHERE id = ?
                """, rs -> rs.next() ? Map.of("id", rs.getString("id"),
                "status", rs.getString("status"), "legacyRole", rs.getString("role")) : null, userId);
    }

    private Map<String, Object> header(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("farmId", rs.getString("farm_id"));
        row.put("authorId", rs.getString("author_user_id"));
        row.put("reportType", rs.getString("report_type"));
        row.put("title", rs.getString("title"));
        row.put("status", rs.getString("status"));
        row.put("legacyStatus", rs.getString("status"));
        row.put("lifecycleStatus", rs.getString("lifecycle_status"));
        row.put("currentVersion", rs.getInt("current_version"));
        Number submittedVersion = (Number) rs.getObject("submitted_version");
        row.put("submittedVersion", submittedVersion == null ? null : submittedVersion.intValue());
        row.put("createdAt", rs.getString("created_at"));
        row.put("updatedAt", rs.getString("updated_at"));
        row.put("submittedAt", rs.getString("submitted_at"));
        row.put("assignedManagerId", rs.getString("assigned_manager_user_id"));
        row.put("scopeType", rs.getString("scope_type"));
        row.put("scopeId", rs.getString("scope_id"));
        row.put("eventDate", rs.getString("event_date"));
        row.put("lateReason", rs.getString("late_reason"));
        row.put("decisionVersion", rs.getObject("decision_version"));
        row.put("mergedAt", rs.getString("merged_at"));
        try {
            row.put("authorUsername", rs.getString("author_username"));
            row.put("authorFullName", rs.getString("author_full_name"));
        } catch (java.sql.SQLException ignored) {
            // Author fields are available only on the manager queue query.
        }
        return row;
    }
}
