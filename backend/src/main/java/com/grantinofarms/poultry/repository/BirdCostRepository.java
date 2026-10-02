package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.domain.BirdCostEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class BirdCostRepository {
    private final JdbcTemplate jdbc;

    public BirdCostRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BirdCostEvent> findEvents(String batchId) {
        return jdbc.query("""
                SELECT event_date, event_type, amount_minor
                FROM bird_cost_events
                WHERE batch_id = ?
                ORDER BY event_date ASC, created_at ASC
                """,
                (rs, rowNum) -> new BirdCostEvent(
                        LocalDate.parse(rs.getString("event_date")),
                        rs.getString("event_type"),
                        rs.getLong("amount_minor")
                ),
                batchId
        );
    }

    public long initialCost(String batchId) {
        Long value = jdbc.queryForObject(
                "SELECT original_purchase_cost_minor FROM batches WHERE id = ?",
                Long.class,
                batchId
        );
        if (value == null) {
            throw new IllegalStateException("Batch purchase cost is required for cost accounting.");
        }
        return value;
    }

    public void insert(
            String id,
            String batchId,
            LocalDate eventDate,
            String eventType,
            long amountMinor,
            String referenceBatchId,
            String reason,
            String createdAt
    ) {
        jdbc.update("""
                INSERT INTO bird_cost_events
                    (id, batch_id, event_date, event_type, amount_minor,
                     reference_batch_id, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                batchId,
                eventDate.toString(),
                eventType,
                amountMinor,
                referenceBatchId,
                reason,
                createdAt
        );
    }
}
