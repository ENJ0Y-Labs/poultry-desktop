package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.domain.FeedPurchaseLot;
import com.grantinofarms.poultry.domain.FeedUsageEvent;
import com.grantinofarms.poultry.dto.FeedTypeResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public class FeedRepository {
    private final JdbcTemplate jdbc;

    public FeedRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FeedTypeResponse> findTypes(String farmId) {
        return jdbc.query("""
                SELECT id, name, unit, applicable_type, status
                FROM feed_types WHERE farm_id = ? ORDER BY name
                """,
                (rs, n) -> new FeedTypeResponse(
                        rs.getString("id"), rs.getString("name"), rs.getString("unit"),
                        rs.getString("applicable_type"), rs.getString("status")
                ), farmId);
    }

    public FeedTypeResponse findType(String id) {
        List<FeedTypeResponse> rows = jdbc.query("""
                SELECT id, name, unit, applicable_type, status
                FROM feed_types WHERE id = ?
                """,
                (rs, n) -> new FeedTypeResponse(
                        rs.getString("id"), rs.getString("name"), rs.getString("unit"),
                        rs.getString("applicable_type"), rs.getString("status")
                ), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public String typeFarmId(String id) {
        return jdbc.queryForObject("SELECT farm_id FROM feed_types WHERE id = ?", String.class, id);
    }

    public String typeUnit(String id) {
        return jdbc.queryForObject("SELECT unit FROM feed_types WHERE id = ?", String.class, id);
    }

    public String typeScope(String id) {
        return jdbc.queryForObject("SELECT applicable_type FROM feed_types WHERE id = ?", String.class, id);
    }

    public void insertType(String id, String farmId, String name, String unit, String scope, String now) {
        jdbc.update("""
                INSERT INTO feed_types
                    (id, farm_id, name, unit, applicable_type, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, id, farmId, name, unit, scope, now, now);
    }

    public void archiveType(String id, String now) {
        jdbc.update("UPDATE feed_types SET status = 'ARCHIVED', updated_at = ? WHERE id = ?", now, id);
    }

    public List<FeedPurchaseLot> findPurchases(String feedTypeId, LocalDate asOf) {
        return jdbc.query("""
                SELECT id, feed_type_id, purchase_date, quantity_milli, total_cost_minor, created_at
                FROM feed_purchases
                WHERE feed_type_id = ? AND purchase_date <= ?
                ORDER BY purchase_date ASC, created_at ASC
                """,
                (rs, n) -> new FeedPurchaseLot(
                        rs.getString("id"), rs.getString("feed_type_id"),
                        LocalDate.parse(rs.getString("purchase_date")),
                        rs.getLong("quantity_milli"), rs.getLong("total_cost_minor"),
                        Instant.parse(rs.getString("created_at"))
                ), feedTypeId, asOf.toString());
    }

    public List<FeedUsageEvent> findUsages(String feedTypeId, LocalDate asOf) {
        return jdbc.query("""
                SELECT id, batch_id, feed_type_id, usage_date, quantity_milli, created_at
                FROM feed_usage
                WHERE feed_type_id = ? AND usage_date <= ?
                ORDER BY usage_date ASC, created_at ASC
                """,
                (rs, n) -> new FeedUsageEvent(
                        rs.getString("id"), rs.getString("batch_id"), rs.getString("feed_type_id"),
                        LocalDate.parse(rs.getString("usage_date")), rs.getLong("quantity_milli"),
                        Instant.parse(rs.getString("created_at"))
                ), feedTypeId, asOf.toString());
    }

    public List<FeedUsageEvent> findBatchUsages(String batchId, LocalDate asOf) {
        return jdbc.query("""
                SELECT id, batch_id, feed_type_id, usage_date, quantity_milli, created_at
                FROM feed_usage
                WHERE batch_id = ? AND usage_date <= ?
                ORDER BY usage_date ASC, created_at ASC
                """,
                (rs, n) -> new FeedUsageEvent(
                        rs.getString("id"), rs.getString("batch_id"), rs.getString("feed_type_id"),
                        LocalDate.parse(rs.getString("usage_date")), rs.getLong("quantity_milli"),
                        Instant.parse(rs.getString("created_at"))
                ), batchId, asOf.toString());
    }

    public void insertPurchase(String id, String farmId, String feedTypeId, String supplierId,
                               LocalDate date, long quantityMilli, String unit, long totalCostMinor, String now) {
        jdbc.update("""
                INSERT INTO feed_purchases
                    (id, farm_id, feed_type_id, supplier_id, purchase_date,
                     quantity_milli, unit, total_cost_minor, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, farmId, feedTypeId, supplierId, date.toString(),
                quantityMilli, unit, totalCostMinor, now);
    }

    public void insertUsage(String id, String batchId, String feedTypeId,
                            LocalDate date, long quantityMilli, String now) {
        jdbc.update("""
                INSERT INTO feed_usage
                    (id, batch_id, feed_type_id, usage_date, quantity_milli, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, batchId, feedTypeId, date.toString(), quantityMilli, now);
    }

    public void insertExpense(String id, String farmId, long amountMinor, LocalDate date,
                              String referenceId, String description, String now) {
        jdbc.update("""
                INSERT INTO expenses
                    (id, farm_id, category, amount_minor, occurred_date,
                     reference_type, reference_id, description, created_at)
                VALUES (?, ?, 'FEED', ?, ?, 'FEED_PURCHASE', ?, ?, ?)
                """, id, farmId, amountMinor, date.toString(), referenceId, description, now);
    }

    public String batchFarmId(String batchId) {
        return jdbc.queryForObject("SELECT farm_id FROM batches WHERE id = ?", String.class, batchId);
    }

    public String batchType(String batchId) {
        return jdbc.queryForObject("SELECT batch_type FROM batches WHERE id = ?", String.class, batchId);
    }

    public LocalDate batchPlacementDate(String batchId) {
        return LocalDate.parse(jdbc.queryForObject(
                "SELECT placement_date FROM batches WHERE id = ?", String.class, batchId));
    }

    public String batchStatus(String batchId) {
        return jdbc.queryForObject("SELECT status FROM batches WHERE id = ?", String.class, batchId);
    }

    public String farmId() {
        List<String> ids = jdbc.query("SELECT id FROM farms WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1",
                (rs, n) -> rs.getString("id"));
        if (ids.isEmpty()) throw new IllegalStateException("No active farm exists.");
        return ids.getFirst();
    }

    public long lastUsageDateEpoch(String feedTypeId) {
        return 0L;
    }
}
