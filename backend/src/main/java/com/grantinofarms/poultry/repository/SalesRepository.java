package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.SaleResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class SalesRepository {
    private final JdbcTemplate jdbc;

    public SalesRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String id, String farmId, String batchId, String customerId,
                       LocalDate date, String saleType, BigDecimal quantity, String unit,
                       long unitPriceMinor, long totalAmountMinor,
                       String referenceType, String referenceId, String createdAt) {
        jdbc.update("""
                INSERT INTO sales (
                    id, farm_id, batch_id, customer_id, sale_date, sale_type,
                    quantity, unit, unit_price_minor, total_amount_minor,
                    reference_type, reference_id, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, farmId, batchId, customerId, date.toString(), saleType,
                quantity, unit, unitPriceMinor, totalAmountMinor,
                referenceType, referenceId, createdAt);
    }

    public List<SaleResponse> findByFarm(String farmId, String batchId, String customerId,
                                         String saleType, LocalDate asOf) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.id, s.farm_id, s.batch_id, s.customer_id,
                       c.name AS customer_name, c.phone AS customer_phone,
                       s.sale_date, s.sale_type, s.quantity, s.unit,
                       s.unit_price_minor, s.total_amount_minor,
                       s.reference_type, s.reference_id
                FROM sales s
                JOIN customers c ON c.id = s.customer_id
                WHERE s.farm_id = ? AND s.sale_date <= ?
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(farmId);
        args.add(asOf.toString());

        if (batchId != null) {
            sql.append(" AND s.batch_id = ?");
            args.add(batchId);
        }
        if (customerId != null) {
            sql.append(" AND s.customer_id = ?");
            args.add(customerId);
        }
        if (saleType != null) {
            sql.append(" AND s.sale_type = ?");
            args.add(saleType);
        }

        sql.append(" ORDER BY s.sale_date DESC, s.created_at DESC");

        return jdbc.query(sql.toString(), (rs, n) -> new SaleResponse(
                rs.getString("id"),
                rs.getString("farm_id"),
                rs.getString("batch_id"),
                rs.getString("customer_id"),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                LocalDate.parse(rs.getString("sale_date")),
                rs.getString("sale_type"),
                rs.getBigDecimal("quantity"),
                rs.getString("unit"),
                rs.getLong("unit_price_minor"),
                rs.getLong("total_amount_minor"),
                rs.getString("reference_type"),
                rs.getString("reference_id")
        ), args.toArray());
    }
}
