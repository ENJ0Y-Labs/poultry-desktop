package com.grantinofarms.poultry.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ReportConflictRepository {
    private final JdbcTemplate jdbc;

    public ReportConflictRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> findDailyRecord(String batchId, String eventDate) {
        return jdbc.query("""
                SELECT id, notes, created_at, updated_at
                FROM daily_records
                WHERE batch_id = ? AND record_date = ?
                LIMIT 1
                """, rs -> {
            if (!rs.next()) return null;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordId", rs.getString("id"));
            row.put("notes", rs.getString("notes"));
            row.put("createdAt", rs.getString("created_at"));
            row.put("updatedAt", rs.getString("updated_at"));
            return row;
        }, batchId, eventDate);
    }

    public List<Map<String, Object>> findExactExpenseDuplicates(
            String farmId, String batchId, String eventDate, long amountMinor,
            String category, String description) {
        return jdbc.query("""
                SELECT id, batch_id, category, amount_minor, occurred_date, description, created_at
                FROM expenses
                WHERE farm_id = ?
                  AND ((batch_id = ?) OR (batch_id IS NULL AND ? IS NULL))
                  AND occurred_date = ?
                  AND amount_minor = ?
                  AND lower(trim(category)) = lower(trim(?))
                  AND lower(trim(description)) = lower(trim(?))
                ORDER BY created_at DESC, id
                LIMIT 5
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordId", rs.getString("id"));
            row.put("batchId", rs.getString("batch_id"));
            row.put("category", rs.getString("category"));
            row.put("amountMinor", rs.getLong("amount_minor"));
            row.put("eventDate", rs.getString("occurred_date"));
            row.put("description", rs.getString("description"));
            row.put("createdAt", rs.getString("created_at"));
            return row;
        }, farmId, batchId, batchId, eventDate, amountMinor, category, description);
    }
}
