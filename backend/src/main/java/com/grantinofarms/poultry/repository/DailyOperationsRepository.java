package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.DailyFeedUsageResponse;
import com.grantinofarms.poultry.dto.DailyPopulationEventResponse;
import com.grantinofarms.poultry.dto.WaterUsageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class DailyOperationsRepository {
    private final JdbcTemplate jdbc;

    public DailyOperationsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String farmIdForBatch(String batchId) {
        return jdbc.query(
                "SELECT farm_id FROM batches WHERE id = ?",
                (rs, n) -> rs.getString("farm_id"), batchId
        ).stream().findFirst().orElse(null);
    }

    public String batchStatus(String batchId) {
        return jdbc.query(
                "SELECT status FROM batches WHERE id = ?",
                (rs, n) -> rs.getString("status"), batchId
        ).stream().findFirst().orElse(null);
    }

    public LocalDate placementDate(String batchId) {
        return jdbc.query(
                "SELECT placement_date FROM batches WHERE id = ?",
                (rs, n) -> LocalDate.parse(rs.getString("placement_date")), batchId
        ).stream().findFirst().orElse(null);
    }

    public String dailyRecordId(String batchId, LocalDate date) {
        return jdbc.query(
                "SELECT id FROM daily_records WHERE batch_id = ? AND record_date = ?",
                (rs, n) -> rs.getString("id"), batchId, date.toString()
        ).stream().findFirst().orElse(null);
    }

    public void insertDailyRecord(String id, String batchId, LocalDate date, String notes, String now) {
        jdbc.update("""
                INSERT INTO daily_records
                    (id, batch_id, record_date, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, batchId, date.toString(), notes, now, now);
    }

    public List<WaterUsageResponse> findWater(String dailyRecordId) {
        return jdbc.query("""
                SELECT w.container_size_id, s.name, s.capacity_units, w.container_count
                FROM water_usage w
                JOIN water_container_sizes s ON s.id = w.container_size_id
                WHERE w.daily_record_id = ?
                ORDER BY s.capacity_units
                """,
                (rs, n) -> {
                    int capacity = rs.getInt("capacity_units");
                    int count = rs.getInt("container_count");
                    return new WaterUsageResponse(
                            rs.getString("container_size_id"),
                            rs.getString("name"),
                            capacity,
                            count,
                            Math.multiplyExact((long) capacity, count)
                    );
                },
                dailyRecordId
        );
    }

    public boolean waterSizeBelongsToFarm(int capacityUnits, String farmId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM water_container_sizes
                WHERE capacity_units = ? AND farm_id = ?
                """, Integer.class, capacityUnits, farmId);
        return count != null && count == 1;
    }

    public String waterSizeId(int capacityUnits, String farmId) {
        return jdbc.query("""
                SELECT id FROM water_container_sizes
                WHERE capacity_units = ? AND farm_id = ?
                """, (rs, n) -> rs.getString("id"), capacityUnits, farmId)
                .stream().findFirst().orElse(null);
    }

    public void insertWater(String id, String dailyRecordId, String sizeId, int count, String now) {
        jdbc.update("""
                INSERT INTO water_usage
                    (id, daily_record_id, container_size_id, container_count, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, dailyRecordId, sizeId, count, now);
    }

    public List<DailyPopulationEventResponse> findPopulationEvents(String batchId, LocalDate date) {
        return jdbc.query("""
                SELECT event_type, event_date, quantity, reason, notes
                FROM bird_population_events
                WHERE batch_id = ? AND event_date = ?
                ORDER BY created_at
                """,
                (rs, n) -> new DailyPopulationEventResponse(
                        rs.getString("event_type"),
                        LocalDate.parse(rs.getString("event_date")),
                        rs.getInt("quantity"),
                        rs.getString("reason"),
                        rs.getString("notes")
                ),
                batchId, date.toString()
        );
    }

    public List<DailyFeedUsageResponse> findFeedUsage(String batchId, LocalDate date) {
        return jdbc.query("""
                SELECT u.feed_type_id, t.name, t.unit, u.usage_date, u.quantity_milli
                FROM feed_usage u
                JOIN feed_types t ON t.id = u.feed_type_id
                WHERE u.batch_id = ? AND u.usage_date = ?
                ORDER BY u.created_at
                """,
                (rs, n) -> {
                    long milli = rs.getLong("quantity_milli");
                    return new DailyFeedUsageResponse(
                            rs.getString("feed_type_id"),
                            rs.getString("name"),
                            rs.getString("unit"),
                            LocalDate.parse(rs.getString("usage_date")),
                            milli,
                            BigDecimal.valueOf(milli, 3)
                    );
                },
                batchId, date.toString()
        );
    }

    public String notes(String batchId, LocalDate date) {
        return jdbc.query(
                "SELECT notes FROM daily_records WHERE batch_id = ? AND record_date = ?",
                (rs, n) -> rs.getString("notes"), batchId, date.toString()
        ).stream().findFirst().orElse(null);
    }

    public List<String> recordDates(String batchId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT DISTINCT record_date FROM (
                    SELECT record_date FROM daily_records WHERE batch_id = ?
                    UNION
                    SELECT event_date FROM bird_population_events WHERE batch_id = ?
                    UNION
                    SELECT usage_date FROM feed_usage WHERE batch_id = ?
                )
                WHERE record_date BETWEEN ? AND ?
                ORDER BY record_date DESC
                """,
                (rs, n) -> rs.getString("record_date"),
                batchId, batchId, batchId, from.toString(), to.toString()
        );
    }
}
