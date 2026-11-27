package com.grantinofarms.poultry.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class WorkflowNotificationService {
    private final JdbcTemplate jdbc;

    public WorkflowNotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void notifyManagers(String farmId, String type, String entityType, String entityId,
                               String title, String message, String now) {
        List<String> managers = jdbc.queryForList("""
                SELECT r.user_id FROM user_roles r
                JOIN users u ON u.id = r.user_id
                WHERE r.role = 'MANAGER' AND u.status = 'ACTIVE'
                """, String.class);
        for (String managerId : managers) {
            insert(farmId, managerId, type, entityType, entityId, title, message, now);
        }
    }

    public void notifyUser(String farmId, String userId, String type, String entityType,
                           String entityId, String title, String message, String now) {
        if (userId == null || userId.isBlank()) return;
        insert(farmId, userId, type, entityType, entityId, title, message, now);
    }

    private void insert(String farmId, String recipientId, String type, String entityType,
                        String entityId, String title, String message, String now) {
        jdbc.update("""
                INSERT OR IGNORE INTO manager_notifications
                    (id, farm_id, recipient_user_id, notification_type, entity_type, entity_id,
                     title, message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), farmId, recipientId, type,
                entityType, entityId, title, message, now);
    }
}
