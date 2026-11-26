package com.grantinofarms.poultry.repository;

import com.fasterxml.jackson.databind.JsonNode;
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
        jdbc.update("""
                INSERT INTO report_submissions
                    (id, farm_id, author_user_id, report_type, title, status, current_version,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'DRAFT', 1, ?, ?)
                """, id, farmId, authorId, reportType, title, now, now);
        insertVersion(id, 1, payloadJson, authorId, now);
        appendEvent(id, 1, authorId, "DRAFT_CREATED", null, now);
    }

    public void insertVersion(String id, int version, String payloadJson, String actorId, String now) {
        jdbc.update("""
                INSERT INTO report_submission_versions
                    (submission_id, version_number, payload_json, created_by, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, version, payloadJson, actorId, now);
    }

    public int updateDraft(String id, String actorId, String reportType, String title,
                           int expectedVersion, int nextVersion, String now) {
        return jdbc.update("""
                UPDATE report_submissions
                SET report_type = ?, title = ?, current_version = ?, status = 'DRAFT', updated_at = ?
                WHERE id = ? AND author_user_id = ? AND status IN ('DRAFT','CHANGES_REQUESTED')
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
                SET status = 'PENDING_REVIEW', submitted_version = ?, submitted_at = ?, updated_at = ?
                WHERE id = ? AND author_user_id = ? AND status = 'DRAFT' AND current_version = ?
                """, version, now, now, id, actorId, version);
    }

    public Map<String, Object> findHeader(String id) {
        return jdbc.query("""
                SELECT id, farm_id, author_user_id, report_type, title, status, current_version,
                       submitted_version, created_at, updated_at, submitted_at
                FROM report_submissions WHERE id = ?
                """, rs -> rs.next() ? header(rs) : null, id);
    }

    public List<Map<String, Object>> findMine(String actorId) {
        return jdbc.query("""
                SELECT id, farm_id, author_user_id, report_type, title, status, current_version,
                       submitted_version, created_at, updated_at, submitted_at
                FROM report_submissions WHERE author_user_id = ?
                ORDER BY updated_at DESC, id
                """, (rs, n) -> header(rs), actorId);
    }

    public List<Map<String, Object>> findVersions(String id) {
        return jdbc.query("""
                SELECT version_number, payload_json, created_by, created_at
                FROM report_submission_versions WHERE submission_id = ?
                ORDER BY version_number
                """, (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("version", rs.getInt("version_number"));
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

    private Map<String, Object> header(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("farmId", rs.getString("farm_id"));
        row.put("authorId", rs.getString("author_user_id"));
        row.put("reportType", rs.getString("report_type"));
        row.put("title", rs.getString("title"));
        row.put("status", rs.getString("status"));
        row.put("currentVersion", rs.getInt("current_version"));
        row.put("submittedVersion", (Integer) rs.getObject("submitted_version"));
        row.put("createdAt", rs.getString("created_at"));
        row.put("updatedAt", rs.getString("updated_at"));
        row.put("submittedAt", rs.getString("submitted_at"));
        return row;
    }
}
