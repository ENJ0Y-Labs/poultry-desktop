package com.grantinofarms.poultry.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ReportingConfigurationRepository {
    private final JdbcTemplate jdbc;

    public ReportingConfigurationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> schedules(String farmId) {
        return jdbc.query("""
                SELECT * FROM reporting_schedules WHERE farm_id = ?
                ORDER BY enabled DESC, report_type, scope_type, due_time
                """, (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id")); row.put("reportType", rs.getString("report_type"));
            row.put("scopeType", rs.getString("scope_type")); row.put("scopeId", rs.getString("scope_id"));
            row.put("batchType", rs.getString("batch_type")); row.put("frequency", rs.getString("frequency"));
            row.put("dueTime", rs.getString("due_time")); row.put("dayOfWeek", rs.getObject("day_of_week"));
            row.put("graceMinutes", rs.getInt("grace_minutes")); row.put("enabled", rs.getInt("enabled") == 1);
            return row;
        }, farmId);
    }

    public void insertSchedule(String id, String farmId, String reportType, String scopeType,
                               String scopeId, String batchType, String frequency, String dueTime,
                               Integer dayOfWeek, int graceMinutes, String actor, String now) {
        jdbc.update("""
                INSERT INTO reporting_schedules
                    (id, farm_id, report_type, scope_type, scope_id, batch_type, frequency,
                     due_time, day_of_week, grace_minutes, enabled, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
                """, id, farmId, reportType, scopeType, scopeId, batchType, frequency,
                dueTime, dayOfWeek, graceMinutes, actor, now, now);
    }

    public int updateScheduleEnabled(String id, String farmId, boolean enabled, String now) {
        return jdbc.update("UPDATE reporting_schedules SET enabled = ?, updated_at = ? WHERE id = ? AND farm_id = ?",
                enabled ? 1 : 0, now, id, farmId);
    }

    public List<Map<String, Object>> thresholds(String farmId) {
        return jdbc.query("""
                SELECT * FROM reporting_thresholds WHERE farm_id = ?
                ORDER BY enabled DESC, threshold_code, scope_type
                """, (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id")); row.put("code", rs.getString("threshold_code"));
            row.put("scopeType", rs.getString("scope_type")); row.put("scopeId", rs.getString("scope_id"));
            row.put("batchType", rs.getString("batch_type")); row.put("value", rs.getDouble("threshold_value"));
            row.put("unit", rs.getString("unit")); row.put("enabled", rs.getInt("enabled") == 1);
            return row;
        }, farmId);
    }

    public void insertThreshold(String id, String farmId, String code, String scopeType, String scopeId,
                                String batchType, double value, String unit, String actor, String now) {
        jdbc.update("""
                INSERT INTO reporting_thresholds
                    (id, farm_id, threshold_code, scope_type, scope_id, batch_type, threshold_value,
                     unit, enabled, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
                """, id, farmId, code, scopeType, scopeId, batchType, value, unit, actor, now, now);
    }

    public int updateThresholdEnabled(String id, String farmId, boolean enabled, String now) {
        return jdbc.update("UPDATE reporting_thresholds SET enabled = ?, updated_at = ? WHERE id = ? AND farm_id = ?",
                enabled ? 1 : 0, now, id, farmId);
    }

    public Map<String, Object> thresholdFor(String farmId, String code, String batchId,
                                            String houseId, String batchType) {
        return jdbc.query("""
                SELECT id, threshold_code, scope_type, scope_id, batch_type, threshold_value, unit
                FROM reporting_thresholds
                WHERE farm_id = ? AND threshold_code = ? AND enabled = 1 AND (
                    (scope_type = 'BATCH' AND scope_id = ?) OR
                    (scope_type = 'HOUSE' AND scope_id = ?) OR
                    (scope_type = 'BATCH_TYPE' AND batch_type = ?) OR
                    (scope_type = 'FARM')
                )
                ORDER BY CASE scope_type WHEN 'BATCH' THEN 0 WHEN 'HOUSE' THEN 1
                         WHEN 'BATCH_TYPE' THEN 2 ELSE 3 END
                LIMIT 1
                """, rs -> {
            if (!rs.next()) return null;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id")); row.put("code", rs.getString("threshold_code"));
            row.put("scopeType", rs.getString("scope_type")); row.put("scopeId", rs.getString("scope_id"));
            row.put("batchType", rs.getString("batch_type")); row.put("value", rs.getDouble("threshold_value"));
            row.put("unit", rs.getString("unit"));
            return row;
        }, farmId, code, batchId, houseId, batchType);
    }

    public boolean hasSubmissionInWindow(String farmId, String reportType, String scopeType,
                                         String scopeId, String batchType, String start, String end) {
        String scopePredicate = switch (scopeType) {
            case "BATCH" -> "scope_type = 'BATCH' AND scope_id = ?";
            case "HOUSE" -> """
                    EXISTS (
                        SELECT 1 FROM report_submission_versions sv
                        JOIN batches b ON b.id = json_extract(sv.payload_json, '$.batchId')
                        WHERE sv.submission_id = report_submissions.id
                          AND sv.version_number = report_submissions.current_version
                          AND b.house_id = ?
                    )
                    """;
            default -> "1 = 1";
        };
        String sql = """
                SELECT COUNT(*) FROM report_submissions
                WHERE farm_id = ? AND report_type = ?
                  AND %s
                  AND (? IS NULL OR EXISTS (
                    SELECT 1 FROM report_submission_versions sv
                    JOIN batches b ON b.id = json_extract(sv.payload_json, '$.batchId')
                    WHERE sv.submission_id = report_submissions.id
                      AND sv.version_number = report_submissions.current_version
                      AND b.batch_type = ?
                  ))
                  AND submitted_at >= ? AND submitted_at < ?
                """.formatted(scopePredicate);
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(farmId);
        args.add(reportType);
        if ("BATCH".equals(scopeType) || "HOUSE".equals(scopeType)) args.add(scopeId);
        args.add(batchType);
        args.add(batchType);
        args.add(start);
        args.add(end);
        Integer count = jdbc.queryForObject(sql, Integer.class, args.toArray());
        return count != null && count > 0;
    }

    public void notifyManagers(String farmId, String entityId, String title, String message, String now) {
        List<String> managers = jdbc.queryForList("""
                SELECT r.user_id FROM user_roles r JOIN users u ON u.id = r.user_id
                WHERE r.role = 'MANAGER' AND u.status = 'ACTIVE'
                """, String.class);
        for (String manager : managers) {
            jdbc.update("""
                    INSERT OR IGNORE INTO manager_notifications
                        (id, farm_id, recipient_user_id, notification_type, entity_type, entity_id,
                         title, message, created_at)
                    VALUES (?, ?, ?, 'MORTALITY_THRESHOLD', 'REPORT_SUBMISSION', ?, ?, ?, ?)
                    """, UUID.randomUUID().toString(), farmId, manager, entityId, title, message, now);
        }
    }
}
