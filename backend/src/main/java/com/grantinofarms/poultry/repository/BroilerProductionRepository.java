package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.BirdSaleResponse;
import com.grantinofarms.poultry.dto.WeightRecordResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Repository
public class BroilerProductionRepository {
    private final JdbcTemplate jdbc;

    public BroilerProductionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String batchFarmId(String batchId) {
        return jdbc.query("SELECT farm_id FROM batches WHERE id = ?",
                (rs, rowNum) -> rs.getString("farm_id"), batchId)
                .stream().findFirst().orElse(null);
    }

    public String batchType(String batchId) {
        return jdbc.query("SELECT batch_type FROM batches WHERE id = ?",
                (rs, rowNum) -> rs.getString("batch_type"), batchId)
                .stream().findFirst().orElse(null);
    }

    public String batchStatus(String batchId) {
        return jdbc.query("SELECT status FROM batches WHERE id = ?",
                (rs, rowNum) -> rs.getString("status"), batchId)
                .stream().findFirst().orElse(null);
    }

    public LocalDate placementDate(String batchId) {
        return jdbc.query("SELECT placement_date FROM batches WHERE id = ?",
                (rs, rowNum) -> LocalDate.parse(rs.getString("placement_date")), batchId)
                .stream().findFirst().orElse(null);
    }

    public int initialBirds(String batchId) {
        Integer value = jdbc.queryForObject(
                "SELECT initial_bird_count FROM batches WHERE id = ?",
                Integer.class, batchId);
        return value == null ? 0 : value;
    }

    public void insertWeight(String id, String batchId, LocalDate date,
                             int sampleQuantity, long totalWeightGrams,
                             String notes, String createdAt) {
        jdbc.update("""
                INSERT INTO weight_records
                    (id, batch_id, record_date, sample_quantity, total_weight_grams, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id, batchId, date.toString(), sampleQuantity, totalWeightGrams, notes, createdAt);
    }

    public List<WeightRecordResponse> findWeights(String batchId, LocalDate asOf) {
        return jdbc.query("""
                SELECT id, batch_id, record_date, sample_quantity, total_weight_grams, notes
                FROM weight_records
                WHERE batch_id = ? AND record_date <= ?
                ORDER BY record_date, created_at
                """,
                (rs, rowNum) -> {
                    BigDecimal total = BigDecimal.valueOf(rs.getLong("total_weight_grams"))
                            .movePointLeft(3);
                    BigDecimal average = total.divide(
                            BigDecimal.valueOf(rs.getInt("sample_quantity")), 6, RoundingMode.HALF_UP);
                    return new WeightRecordResponse(
                            rs.getString("id"),
                            rs.getString("batch_id"),
                            LocalDate.parse(rs.getString("record_date")),
                            rs.getInt("sample_quantity"),
                            total,
                            average,
                            null,
                            rs.getString("notes")
                    );
                },
                batchId, asOf.toString());
    }

    public void insertSale(String id, String batchId, LocalDate date, int quantity,
                           long pricePerBirdMinor, long totalAmountMinor,
                           String customer, String customerId, String createdAt) {
        jdbc.update("""
                INSERT INTO bird_sales
                    (id, batch_id, record_date, quantity, price_per_bird_minor,
                     total_amount_minor, customer, customer_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, batchId, date.toString(), quantity, pricePerBirdMinor,
                totalAmountMinor, customer, customerId, createdAt);
    }

    public List<BirdSaleResponse> findSales(String batchId, LocalDate asOf) {
        return jdbc.query("""
                SELECT id, batch_id, record_date, quantity,
                       price_per_bird_minor, total_amount_minor, customer
                FROM bird_sales
                WHERE batch_id = ? AND record_date <= ?
                ORDER BY record_date, created_at
                """,
                (rs, rowNum) -> new BirdSaleResponse(
                        rs.getString("id"),
                        rs.getString("batch_id"),
                        LocalDate.parse(rs.getString("record_date")),
                        rs.getInt("quantity"),
                        rs.getLong("price_per_bird_minor"),
                        rs.getLong("total_amount_minor"),
                        rs.getString("customer")
                ),
                batchId, asOf.toString());
    }

    public BigDecimal feedConsumedKg(String batchId, LocalDate asOf) {
        List<BigDecimal> quantities = jdbc.query("""
                SELECT ft.unit, fu.quantity_milli
                FROM feed_usage fu
                JOIN feed_types ft ON ft.id = fu.feed_type_id
                WHERE fu.batch_id = ?
                  AND fu.usage_date <= ?
                """,
                (rs, rowNum) -> {
                    if (!"kg".equalsIgnoreCase(rs.getString("unit"))) {
                        return null;
                    }
                    return BigDecimal.valueOf(rs.getLong("quantity_milli")).movePointLeft(3);
                },
                batchId, asOf.toString());

        return quantities.stream()
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
