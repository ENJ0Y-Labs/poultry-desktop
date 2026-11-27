package com.grantinofarms.poultry.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class FarmIncidentRepository {
    private final JdbcTemplate jdbc;

    public FarmIncidentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(String id, String farmId, String authorId, String type, String severity,
                       String title, String description, String batchId, String houseId,
                       String eventAt, String now) {
        jdbc.update("""
                INSERT INTO farm_incidents
                    (id, farm_id, reported_by, incident_type, severity, title, description,
                     batch_id, house_id, event_at, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?)
                """, id, farmId, authorId, type, severity, title, description, batchId, houseId, eventAt, now, now);
    }

    public Map<String, Object> find(String id) {
        return jdbc.query("""
                SELECT i.*, u.username AS reporter_username, u.full_name AS reporter_name
                FROM farm_incidents i JOIN users u ON u.id = i.reported_by
                WHERE i.id = ?
                """, rs -> rs.next() ? map(rs) : null, id);
    }

    public List<Map<String, Object>> list(String farmId, String authorId, boolean manager) {
        String sql = """
                SELECT i.*, u.username AS reporter_username, u.full_name AS reporter_name
                FROM farm_incidents i JOIN users u ON u.id = i.reported_by
                WHERE i.farm_id = ? AND (? = 1 OR i.reported_by = ?)
                ORDER BY CASE i.severity WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 ELSE 2 END,
                         i.created_at DESC
                """;
        return jdbc.query(sql, (rs, n) -> map(rs), farmId, manager ? 1 : 0, authorId);
    }

    public void appendEvent(String id, String incidentId, String actor, String type, String details, String now) {
        jdbc.update("""
                INSERT INTO farm_incident_events (id, incident_id, actor_user_id, event_type, details_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, incidentId, actor, type, details, now);
    }

    public List<Map<String, Object>> events(String id) {
        return jdbc.query("""
                SELECT e.id, e.actor_user_id, u.full_name AS actor_name, e.event_type, e.details_json, e.created_at
                FROM farm_incident_events e JOIN users u ON u.id = e.actor_user_id
                WHERE e.incident_id = ? ORDER BY e.created_at, e.id
                """, (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("actorId", rs.getString("actor_user_id"));
            row.put("actorName", rs.getString("actor_name"));
            row.put("event", rs.getString("event_type"));
            row.put("details", rs.getString("details_json"));
            row.put("createdAt", rs.getString("created_at"));
            return row;
        }, id);
    }

    public int acknowledge(String id, String actor, String now) {
        return jdbc.update("""
                UPDATE farm_incidents SET status = 'ACKNOWLEDGED', acknowledged_by = ?,
                    acknowledged_at = ?, updated_at = ?
                WHERE id = ? AND status = 'OPEN'
                """, actor, now, now, id);
    }

    public int resolve(String id, String actor, String notes, String now) {
        return jdbc.update("""
                UPDATE farm_incidents SET status = 'RESOLVED', resolved_by = ?,
                    resolved_at = ?, resolution_notes = ?, updated_at = ?
                WHERE id = ? AND status IN ('OPEN','ACKNOWLEDGED','IN_PROGRESS')
                """, actor, now, notes, now, id);
    }

    public void notifyManagers(String farmId, String incidentId, String severity,
                               String title, String message, String now) {
        List<String> managers = jdbc.queryForList("""
                SELECT r.user_id FROM user_roles r JOIN users u ON u.id = r.user_id
                WHERE r.role = 'MANAGER' AND u.status = 'ACTIVE'
                """, String.class);
        for (String managerId : managers) {
            jdbc.update("""
                    INSERT INTO manager_notifications
                        (id, farm_id, recipient_user_id, notification_type, entity_type, entity_id,
                         title, message, created_at)
                    VALUES (?, ?, ?, 'EMERGENCY_INCIDENT', 'FARM_INCIDENT', ?, ?, ?, ?)
                    ON CONFLICT(recipient_user_id, notification_type, entity_id, title)
                    DO UPDATE SET message = excluded.message, created_at = excluded.created_at, read_at = NULL
                    """, UUID.randomUUID().toString(), farmId, managerId, incidentId,
                    severity + ": " + title, message, now);
        }
    }

    public List<Map<String, Object>> notifications(String userId) {
        return jdbc.query("""
                SELECT id, notification_type, entity_type, entity_id, title, message, created_at, read_at
                FROM manager_notifications WHERE recipient_user_id = ?
                ORDER BY read_at IS NULL DESC, created_at DESC LIMIT 100
                """, (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("type", rs.getString("notification_type"));
            row.put("entityType", rs.getString("entity_type"));
            row.put("entityId", rs.getString("entity_id"));
            row.put("title", rs.getString("title"));
            row.put("message", rs.getString("message"));
            row.put("createdAt", rs.getString("created_at"));
            row.put("readAt", rs.getString("read_at"));
            return row;
        }, userId);
    }

    public int markNotificationRead(String id, String userId, String now) {
        return jdbc.update("""
                UPDATE manager_notifications SET read_at = ?
                WHERE id = ? AND recipient_user_id = ? AND read_at IS NULL
                """, now, id, userId);
    }

    private Map<String, Object> map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("farmId", rs.getString("farm_id"));
        row.put("reportedBy", rs.getString("reported_by"));
        row.put("reporterUsername", rs.getString("reporter_username"));
        row.put("reporterName", rs.getString("reporter_name"));
        row.put("incidentType", rs.getString("incident_type"));
        row.put("severity", rs.getString("severity"));
        row.put("title", rs.getString("title"));
        row.put("description", rs.getString("description"));
        row.put("batchId", rs.getString("batch_id"));
        row.put("houseId", rs.getString("house_id"));
        row.put("eventAt", rs.getString("event_at"));
        row.put("status", rs.getString("status"));
        row.put("acknowledgedBy", rs.getString("acknowledged_by"));
        row.put("acknowledgedAt", rs.getString("acknowledged_at"));
        row.put("resolvedBy", rs.getString("resolved_by"));
        row.put("resolvedAt", rs.getString("resolved_at"));
        row.put("resolutionNotes", rs.getString("resolution_notes"));
        row.put("createdAt", rs.getString("created_at"));
        return row;
    }
}
