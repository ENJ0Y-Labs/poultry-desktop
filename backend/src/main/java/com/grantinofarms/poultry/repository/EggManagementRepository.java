package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.EggCollectionResponse;
import com.grantinofarms.poultry.dto.EggSaleResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class EggManagementRepository {
    private final JdbcTemplate jdbc;

    public EggManagementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String farmIdForBatch(String batchId) {
        List<String> ids = jdbc.query(
                "SELECT farm_id FROM batches WHERE id = ?",
                (rs, n) -> rs.getString("farm_id"), batchId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public String batchType(String batchId) {
        List<String> types = jdbc.query(
                "SELECT batch_type FROM batches WHERE id = ?",
                (rs, n) -> rs.getString("batch_type"), batchId);
        return types.isEmpty() ? null : types.getFirst();
    }

    public String batchStatus(String batchId) {
        List<String> statuses = jdbc.query(
                "SELECT status FROM batches WHERE id = ?",
                (rs, n) -> rs.getString("status"), batchId);
        return statuses.isEmpty() ? null : statuses.getFirst();
    }

    public LocalDate placementDate(String batchId) {
        return LocalDate.parse(jdbc.queryForObject(
                "SELECT placement_date FROM batches WHERE id = ?", String.class, batchId));
    }

    public void insertCollection(String id, String batchId, LocalDate date, int good,
                                 int cracked, String notes, String now) {
        jdbc.update("""
                INSERT INTO egg_collections
                    (id, batch_id, record_date, good_eggs, cracked_eggs, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, batchId, date.toString(), good, cracked, notes, now);
    }

    public List<EggCollectionResponse> findCollections(String batchId, LocalDate asOf) {
        return jdbc.query("""
                SELECT id, batch_id, record_date, good_eggs, cracked_eggs, notes
                FROM egg_collections
                WHERE batch_id = ? AND record_date <= ?
                ORDER BY record_date DESC, created_at DESC
                """, (rs, n) -> {
            int good = rs.getInt("good_eggs");
            int cracked = rs.getInt("cracked_eggs");
            return new EggCollectionResponse(
                    rs.getString("id"),
                    rs.getString("batch_id"),
                    LocalDate.parse(rs.getString("record_date")),
                    good,
                    cracked,
                    good + cracked,
                    rs.getString("notes")
            );
        }, batchId, asOf.toString());
    }

    public void insertSale(String id, String batchId, LocalDate date, String customer,
                           int soldEggs, int crateSize, long pricePerCrateMinor,
                           long totalAmountMinor, String now) {
        jdbc.update("""
                INSERT INTO egg_sales
                    (id, batch_id, record_date, customer, sold_eggs, crate_size,
                     price_per_crate_minor, total_amount_minor, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, batchId, date.toString(), customer, soldEggs, crateSize,
                pricePerCrateMinor, totalAmountMinor, now);
    }

    public List<EggSaleResponse> findSales(String batchId, LocalDate asOf) {
        return jdbc.query("""
                SELECT id, batch_id, record_date, customer, sold_eggs, crate_size,
                       price_per_crate_minor, total_amount_minor
                FROM egg_sales
                WHERE batch_id = ? AND record_date <= ?
                ORDER BY record_date DESC, created_at DESC
                """, (rs, n) -> {
            int eggs = rs.getInt("sold_eggs");
            int crateSize = rs.getInt("crate_size");
            return new EggSaleResponse(
                    rs.getString("id"),
                    rs.getString("batch_id"),
                    LocalDate.parse(rs.getString("record_date")),
                    rs.getString("customer"),
                    BigDecimal.valueOf(eggs).divide(BigDecimal.valueOf(crateSize)),
                    eggs,
                    crateSize,
                    rs.getLong("price_per_crate_minor"),
                    rs.getLong("total_amount_minor")
            );
        }, batchId, asOf.toString());
    }

    public int goodCollectedThrough(String batchId, LocalDate date) {
        Integer value = jdbc.queryForObject(
                "SELECT COALESCE(SUM(good_eggs), 0) FROM egg_collections WHERE batch_id = ? AND record_date <= ?",
                Integer.class, batchId, date.toString());
        return value == null ? 0 : value;
    }

    public int goodSoldThrough(String batchId, LocalDate date) {
        Integer value = jdbc.queryForObject(
                "SELECT COALESCE(SUM(sold_eggs), 0) FROM egg_sales WHERE batch_id = ? AND record_date <= ?",
                Integer.class, batchId, date.toString());
        return value == null ? 0 : value;
    }

    public int goodCollected(String batchId, LocalDate asOf) {
        return goodCollectedThrough(batchId, asOf);
    }

    public int crackedCollected(String batchId, LocalDate asOf) {
        Integer value = jdbc.queryForObject(
                "SELECT COALESCE(SUM(cracked_eggs), 0) FROM egg_collections WHERE batch_id = ? AND record_date <= ?",
                Integer.class, batchId, asOf.toString());
        return value == null ? 0 : value;
    }

    public int goodSold(String batchId, LocalDate asOf) {
        return goodSoldThrough(batchId, asOf);
    }
}
