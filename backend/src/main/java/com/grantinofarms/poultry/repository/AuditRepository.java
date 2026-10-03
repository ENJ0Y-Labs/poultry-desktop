package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.service.CurrentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class AuditRepository {
    private final JdbcTemplate jdbc;
    public AuditRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void append(String farmId, String action, String entityType, String entityId,
                       String reason, String beforeJson, String afterJson, String now) {
        jdbc.update("""
                INSERT INTO audit_logs
                    (id, user_id, farm_id, occurred_at, action, entity_type, entity_id,
                     reason, changed_fields_json, before_json, after_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), CurrentUserContext.get(), farmId, now,
                action, entityType, entityId, reason, null, beforeJson, afterJson);
    }

    public List<Map<String, Object>> find(String farmId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return jdbc.query("""
                SELECT id, user_id, farm_id, batch_id, occurred_at, action, entity_type,
                       entity_id, reason, changed_fields_json, before_json, after_json
                FROM audit_logs
                WHERE farm_id = ?
                ORDER BY occurred_at DESC
                LIMIT ?
                """, (rs, n) -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("userId", rs.getString("user_id"));
            row.put("farmId", rs.getString("farm_id"));
            row.put("batchId", rs.getString("batch_id"));
            row.put("occurredAt", rs.getString("occurred_at"));
            row.put("action", rs.getString("action"));
            row.put("entityType", rs.getString("entity_type"));
            row.put("entityId", rs.getString("entity_id"));
            row.put("reason", rs.getString("reason"));
            row.put("changedFields", rs.getString("changed_fields_json"));
            row.put("before", rs.getString("before_json"));
            row.put("after", rs.getString("after_json"));
            return row;
        }, farmId, safeLimit);
    }
}
