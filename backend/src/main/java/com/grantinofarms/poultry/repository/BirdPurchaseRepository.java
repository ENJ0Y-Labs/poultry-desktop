package com.grantinofarms.poultry.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public class BirdPurchaseRepository {
    private final JdbcTemplate jdbc;

    public BirdPurchaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(
            String id,
            String batchId,
            String supplierId,
            LocalDate purchaseDate,
            int quantity,
            long unitCostMinor,
            long totalCostMinor,
            String createdAt
    ) {
        jdbc.update("""
                INSERT INTO bird_purchases
                    (id, batch_id, supplier_id, purchase_date, quantity,
                     unit_cost_minor, total_cost_minor, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, batchId, supplierId, purchaseDate.toString(), quantity,
                unitCostMinor, totalCostMinor, createdAt
        );
    }
}
