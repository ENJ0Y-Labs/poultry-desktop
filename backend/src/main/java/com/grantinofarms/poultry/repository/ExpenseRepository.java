package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.ExpenseResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class ExpenseRepository {
    private final JdbcTemplate jdbc;

    public ExpenseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String id, String farmId, String batchId, LocalDate occurredDate,
                       String description, long amountMinor, String category, String now) {
        jdbc.update("""
                INSERT INTO expenses
                    (id, farm_id, batch_id, category, amount_minor, occurred_date,
                     reference_type, reference_id, description, created_at)
                VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?)
                """,
                id, farmId, batchId, category, amountMinor, occurredDate.toString(),
                description, now);
    }

    public List<ExpenseResponse> findByFarm(String farmId, String batchId, LocalDate asOf) {
        String sql = """
                SELECT id, farm_id, batch_id, occurred_date, description, amount_minor,
                       category, reference_type, reference_id, created_at
                FROM expenses
                WHERE farm_id = ?
                """ + (batchId == null ? "" : " AND batch_id = ? ")
                + (asOf == null ? "" : " AND occurred_date <= ? ") + """
                ORDER BY occurred_date DESC, created_at DESC
                """;

        if (batchId == null && asOf == null) {
            return jdbc.query(sql, (rs, n) -> map(rs), farmId);
        }
        if (batchId == null) {
            return jdbc.query(sql, (rs, n) -> map(rs), farmId, asOf.toString());
        }
        if (asOf == null) {
            return jdbc.query(sql, (rs, n) -> map(rs), farmId, batchId);
        }
        return jdbc.query(sql, (rs, n) -> map(rs), farmId, batchId, asOf.toString());
    }

    private ExpenseResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ExpenseResponse(
                rs.getString("id"),
                rs.getString("farm_id"),
                rs.getString("batch_id"),
                LocalDate.parse(rs.getString("occurred_date")),
                rs.getString("description"),
                rs.getLong("amount_minor"),
                rs.getString("category"),
                rs.getString("reference_type"),
                rs.getString("reference_id"),
                rs.getString("created_at")
        );
    }
}
