package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.domain.FeedPurchaseLot;
import com.grantinofarms.poultry.domain.FeedOpeningStockLot;
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
        List<FeedTypeResponse> rows = jdbc.query("""
                SELECT id, name, unit, applicable_type, status, kg_per_unit, brand, packaging_name, package_weight_kg
                FROM feed_types WHERE farm_id = ? ORDER BY name
                """,
                (rs, n) -> new FeedTypeResponse(
                        rs.getString("id"), rs.getString("name"), rs.getString("unit"),
                        rs.getString("applicable_type"), rs.getString("status"), rs.getBigDecimal("kg_per_unit"),
                        rs.getString("brand"), rs.getString("packaging_name"), rs.getBigDecimal("package_weight_kg")
                ), farmId);
        return rows.stream().map(this::withApplicability).toList();
    }

    public FeedTypeResponse findType(String id) {
        List<FeedTypeResponse> rows = jdbc.query("""
                SELECT id, name, unit, applicable_type, status, kg_per_unit, brand, packaging_name, package_weight_kg
                FROM feed_types WHERE id = ?
                """,
                (rs, n) -> new FeedTypeResponse(
                        rs.getString("id"), rs.getString("name"), rs.getString("unit"),
                        rs.getString("applicable_type"), rs.getString("status"), rs.getBigDecimal("kg_per_unit"),
                        rs.getString("brand"), rs.getString("packaging_name"), rs.getBigDecimal("package_weight_kg")
                ), id);
        return rows.isEmpty() ? null : withApplicability(rows.get(0));
    }

    private FeedTypeResponse withApplicability(FeedTypeResponse type) {
        return new FeedTypeResponse(type.id(), type.name(), type.unit(), type.applicableType(), type.status(),
                type.kgPerUnit(), type.brand(), type.packagingName(), type.packageWeightKg(),
                findApplicableTypes(type.id()));
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

    public void insertType(String id, String farmId, String name, String unit, String legacyScope,
                           java.math.BigDecimal kgPerUnit, String brand, String packagingName,
                           java.math.BigDecimal packageWeightKg, List<String> applicableTypes, String now) {
        jdbc.update("""
                INSERT INTO feed_types
                    (id, farm_id, name, unit, applicable_type, status, kg_per_unit, brand, packaging_name, package_weight_kg, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?)
                """, id, farmId, name, unit, legacyScope, kgPerUnit, brand, packagingName, packageWeightKg, now, now);
        for (String birdType : applicableTypes) {
            jdbc.update("INSERT INTO feed_type_applicability (feed_type_id, bird_type) VALUES (?, ?)", id, birdType);
        }
    }

    public boolean productIdentityExists(String farmId, String name, String brand, String excludingId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM feed_types
                WHERE farm_id = ?
                  AND lower(trim(name)) = lower(trim(?))
                  AND lower(trim(COALESCE(brand, ''))) = lower(trim(COALESCE(?, '')))
                  AND (? IS NULL OR id <> ?)
                """, Integer.class, farmId, name, brand, excludingId, excludingId);
        return count != null && count > 0;
    }

    public void updateProductIdentity(String id, String name, String brand, String packagingName,
                                      java.math.BigDecimal packageWeightKg, String now) {
        jdbc.update("""
                UPDATE feed_types
                SET name = ?, brand = ?, packaging_name = ?, package_weight_kg = ?, updated_at = ?
                WHERE id = ?
                """, name, brand, packagingName, packageWeightKg, now, id);
    }

    public List<String> findApplicableTypes(String feedTypeId) {
        return jdbc.query("SELECT bird_type FROM feed_type_applicability WHERE feed_type_id = ? ORDER BY CASE bird_type WHEN 'LAYER' THEN 1 WHEN 'BROILER' THEN 2 WHEN 'TURKEY' THEN 3 END",
                (rs, n) -> rs.getString("bird_type"), feedTypeId);
    }


    public void insertOpeningBalance(String id, String farmId, String feedTypeId, LocalDate date,
                                    long quantityMilli, String unit, String reason, String now) {
        jdbc.update("""
                INSERT INTO feed_opening_balances
                    (id, farm_id, feed_type_id, effective_date, quantity_milli, unit, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, farmId, feedTypeId, date.toString(), quantityMilli, unit, reason, now);
    }

    public List<com.grantinofarms.poultry.dto.FeedOpeningBalanceResponse> findOpeningBalances(String feedTypeId) {
        return jdbc.query("""
                SELECT b.id, b.feed_type_id, t.name AS feed_type_name, t.brand, b.effective_date,
                       b.quantity_milli, b.unit, b.reason, b.created_at
                FROM feed_opening_balances b
                JOIN feed_types t ON t.id = b.feed_type_id
                WHERE b.feed_type_id = ?
                ORDER BY b.effective_date, b.created_at
                """, (rs, n) -> new com.grantinofarms.poultry.dto.FeedOpeningBalanceResponse(
                rs.getString("id"), rs.getString("feed_type_id"), rs.getString("feed_type_name"),
                LocalDate.parse(rs.getString("effective_date")), rs.getLong("quantity_milli"),
                rs.getString("unit"), rs.getString("reason"), rs.getString("created_at"), rs.getString("brand")
        ), feedTypeId);
    }

    public List<FeedOpeningStockLot> findOpeningLots(String feedTypeId, LocalDate asOf) {
        return jdbc.query("""
                SELECT id, feed_type_id, effective_date, quantity_milli, created_at
                FROM feed_opening_balances
                WHERE feed_type_id = ? AND effective_date <= ?
                ORDER BY effective_date ASC, created_at ASC
                """,
                (rs, n) -> new FeedOpeningStockLot(
                        rs.getString("id"), rs.getString("feed_type_id"),
                        LocalDate.parse(rs.getString("effective_date")),
                        rs.getLong("quantity_milli"), Instant.parse(rs.getString("created_at"))
                ), feedTypeId, asOf.toString());
    }

    public LocalDate latestOpeningBalanceDate(String feedTypeId) {
        List<String> dates = jdbc.query(
                "SELECT effective_date FROM feed_opening_balances WHERE feed_type_id = ? ORDER BY effective_date DESC, created_at DESC LIMIT 1",
                (rs, n) -> rs.getString("effective_date"), feedTypeId);
        return dates.isEmpty() ? null : LocalDate.parse(dates.get(0));
    }

    public void updateKgPerUnit(String id, java.math.BigDecimal kgPerUnit, String now) {
        jdbc.update("UPDATE feed_types SET kg_per_unit = ?, updated_at = ? WHERE id = ?", kgPerUnit, now, id);
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
                            LocalDate date, long quantityMilli, String reason, String now) {
        jdbc.update("""
                INSERT INTO feed_usage
                    (id, batch_id, feed_type_id, usage_date, quantity_milli, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, batchId, feedTypeId, date.toString(), quantityMilli, reason, now);
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
        return ids.get(0);
    }

    public boolean supplierBelongsToFarm(String supplierId, String farmId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM suppliers WHERE id = ? AND farm_id = ? AND status = 'ACTIVE'",
                Integer.class, supplierId, farmId);
        return count != null && count == 1;
    }


    public boolean purchaseExists(String feedTypeId, String supplierId, LocalDate date, long quantityMilli, long costMinor) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM feed_purchases
                WHERE feed_type_id = ? AND supplier_id IS ? AND purchase_date = ?
                  AND quantity_milli = ? AND total_cost_minor = ?
                """, Integer.class, feedTypeId, supplierId, date.toString(), quantityMilli, costMinor);
        return count != null && count > 0;
    }

    public boolean usageExists(String batchId, String feedTypeId, LocalDate date, long quantityMilli, String reason) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM feed_usage
                WHERE batch_id = ? AND feed_type_id = ? AND usage_date = ?
                  AND quantity_milli = ? AND reason = ?
                """, Integer.class, batchId, feedTypeId, date.toString(), quantityMilli, reason);
        return count != null && count > 0;
    }

    public LocalDate latestUsageDate(String feedTypeId) {
        List<String> dates = jdbc.query(
                "SELECT usage_date FROM feed_usage WHERE feed_type_id = ? ORDER BY usage_date DESC, created_at DESC LIMIT 1",
                (rs, n) -> rs.getString("usage_date"), feedTypeId);
        return dates.isEmpty() ? null : LocalDate.parse(dates.get(0));
    }

    public LocalDate latestPurchaseDate(String feedTypeId) {
        List<String> dates = jdbc.query(
                "SELECT purchase_date FROM feed_purchases WHERE feed_type_id = ? ORDER BY purchase_date DESC, created_at DESC LIMIT 1",
                (rs, n) -> rs.getString("purchase_date"), feedTypeId);
        return dates.isEmpty() ? null : LocalDate.parse(dates.get(0));
    }
}
