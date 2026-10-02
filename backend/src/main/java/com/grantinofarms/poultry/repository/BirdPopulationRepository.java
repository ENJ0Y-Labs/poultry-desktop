package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.domain.BirdPopulationEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class BirdPopulationRepository {
    private final JdbcTemplate jdbc;

    public BirdPopulationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BirdPopulationEvent> findEvents(String batchId) {
        return jdbc.query("""
                SELECT event_date, event_type, quantity
                FROM bird_population_events
                WHERE batch_id = ?
                ORDER BY event_date ASC, created_at ASC
                """,
                (rs, rowNum) -> new BirdPopulationEvent(
                        LocalDate.parse(rs.getString("event_date")),
                        rs.getString("event_type"),
                        rs.getInt("quantity")
                ),
                batchId
        );
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
            String createdAt
    ) {
        jdbc.update("""
                INSERT INTO bird_population_events
                    (id, batch_id, event_date, event_type, quantity,
                     reference_batch_id, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                batchId,
                eventDate.toString(),
                eventType,
                quantity,
                referenceBatchId,
                reason,
                createdAt
        );
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
