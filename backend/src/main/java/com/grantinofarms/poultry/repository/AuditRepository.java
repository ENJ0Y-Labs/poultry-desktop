package com.grantinofarms.poultry.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class AuditRepository {
    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(String farmId, String action, String entityType, String entityId,
                       String reason, String beforeJson, String afterJson, String now) {
        jdbc.update("""
                INSERT INTO audit_logs
                    (id, farm_id, occurred_at, action, entity_type, entity_id, reason, before_json, after_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), farmId, now, action, entityType, entityId,
                reason, beforeJson, afterJson);
    }
}
