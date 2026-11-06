package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.domain.BirdPopulationEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import com.grantinofarms.poultry.dto.PopulationAdjustmentResponse;

@Repository
public class BirdPopulationRepository {
    private final JdbcTemplate jdbc;

    public BirdPopulationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BirdPopulationEvent> findEvents(String batchId) {
        return jdbc.query("""
                SELECT event_date, event_type, quantity, created_at
                FROM bird_population_events WHERE batch_id = ?
                UNION ALL
                SELECT effective_date AS event_date, 'ADJUSTMENT' AS event_type,
                       difference AS quantity, created_at
                FROM bird_population_adjustments WHERE batch_id = ?
                ORDER BY event_date ASC, created_at ASC
                """,
                (rs, rowNum) -> new BirdPopulationEvent(
                        LocalDate.parse(rs.getString("event_date")),
                        rs.getString("event_type"),
                        rs.getInt("quantity")
                ),
                batchId, batchId
        );
    }

    public String populationCountStatus(String batchId) {
        return jdbc.queryForObject("SELECT population_count_status FROM batches WHERE id=?", String.class, batchId);
    }

    public int initialBirds(String batchId) {
        Integer value = jdbc.queryForObject(
                "SELECT initial_bird_count FROM batches WHERE id = ?",
                Integer.class,
                batchId
        );
        return value == null ? 0 : value;
    }

    public String batchFarmId(String batchId) {
        return jdbc.query("""
                SELECT farm_id FROM batches WHERE id = ?
                """,
                (rs, rowNum) -> rs.getString("farm_id"),
                batchId
        ).stream().findFirst().orElse(null);
    }

    public boolean batchExists(String batchId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM batches WHERE id = ?",
                Integer.class,
                batchId
        );
        return count != null && count == 1;
    }

    public String batchStatus(String batchId) {
        return jdbc.query(
                "SELECT status FROM batches WHERE id = ?",
                (rs, rowNum) -> rs.getString("status"),
                batchId
        ).stream().findFirst().orElse(null);
    }

    public void insert(
            String id,
            String batchId,
            LocalDate eventDate,
            String eventType,
            int quantity,
            String referenceBatchId,
            String reason,
            String notes,
            String createdAt
    ) {
        jdbc.update("""
                INSERT INTO bird_population_events
                    (id, batch_id, event_date, event_type, quantity,
                     reference_batch_id, reason, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                batchId,
                eventDate.toString(),
                eventType,
                quantity,
                referenceBatchId,
                reason,
                notes,
                createdAt
        );
    }

    public void insertAdjustment(String id, String batchId, LocalDate effectiveDate,
                                 int previousCount, int newCount, int difference,
                                 String reason, String userId, String createdAt) {
        jdbc.update("""
                INSERT INTO bird_population_adjustments
                    (id, batch_id, effective_date, previous_count, new_count, difference, reason, user_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, batchId, effectiveDate.toString(), previousCount, newCount,
                difference, reason, userId, createdAt);
    }

    public List<PopulationAdjustmentResponse> findAdjustments(String batchId) {
        return jdbc.query("""
                SELECT id, batch_id, effective_date, previous_count, new_count, difference, reason, user_id, created_at
                FROM bird_population_adjustments WHERE batch_id=?
                ORDER BY effective_date DESC, created_at DESC
                """, (rs, n) -> new PopulationAdjustmentResponse(
                rs.getString("id"), rs.getString("batch_id"), LocalDate.parse(rs.getString("effective_date")),
                rs.getInt("previous_count"), rs.getInt("new_count"), rs.getInt("difference"),
                rs.getString("reason"), rs.getString("user_id"), rs.getString("created_at")), batchId);
    }

    public int countEvents(String batchId, String eventType, LocalDate asOf) {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(quantity), 0)
                FROM bird_population_events
                WHERE batch_id = ?
                  AND event_type = ?
                  AND event_date <= ?
                """,
                Integer.class,
                batchId,
                eventType,
                asOf.toString()
        );
        return value == null ? 0 : value;
    }
}
