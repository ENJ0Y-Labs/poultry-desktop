package com.grantinofarms.poultry;

import com.grantinofarms.poultry.repository.BatchRepository;
import com.grantinofarms.poultry.repository.CustomerRepository;
import com.grantinofarms.poultry.repository.SalesRepository;
import com.grantinofarms.poultry.service.BackupService;
import com.grantinofarms.poultry.service.DashboardService;
import com.grantinofarms.poultry.service.ReportService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/phase35-performance.db?foreign_keys=on&journal_mode=WAL&synchronous=FULL",
        "poultry.database-path=target/phase35-performance.db",
        "poultry.auth-required=false",
        "logging.level.root=WARN"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PerformanceBaselineTest {

    private static final Path DATABASE = Path.of("target/phase35-performance.db");
    private static final Path BACKUP_DIR = Path.of("target/phase35-backups");
    private static final LocalDate START = LocalDate.of(2023, 1, 1);
    private static final LocalDate AS_OF = LocalDate.of(2025, 12, 31);
    private static String farmId;
    private static String layerBatchId;
    private static String broilerBatchId;
    private static String customerId;

    @Autowired JdbcTemplate jdbc;
    @Autowired DashboardService dashboard;
    @Autowired ReportService reports;
    @Autowired BatchRepository batches;
    @Autowired SalesRepository sales;
    @Autowired CustomerRepository customers;
    @Autowired BackupService backups;

    @BeforeAll
    static void cleanPerformanceFiles() throws IOException {
        Files.createDirectories(DATABASE.getParent());
        Files.deleteIfExists(DATABASE);
        Files.deleteIfExists(Path.of(DATABASE + "-wal"));
        Files.deleteIfExists(Path.of(DATABASE + "-shm"));
        if (Files.exists(BACKUP_DIR)) {
            try (var paths = Files.list(BACKUP_DIR)) {
                paths.forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                });
            }
        } else {
            Files.createDirectories(BACKUP_DIR);
        }
    }

    @Test
    void realisticDatasetMeetsPhase35Baselines() throws Exception {
        seedDataset();

        long dashboardMs = timed(() -> dashboard.farm(AS_OF));
        long layerBatchMs = timed(() -> dashboard.batch(layerBatchId, AS_OF));
        long broilerBatchMs = timed(() -> dashboard.batch(broilerBatchId, AS_OF));
        long reportMs = timed(() -> reports.farm(AS_OF));
        long filteredSearchMs = timed(() -> sales.findByFarm(
                farmId, broilerBatchId, customerId, "BROILER", AS_OF));
        long customerSearchMs = timed(() -> customers.findByName(farmId, "Customer 004"));
        long backupMs = timed(() -> backups.create(BACKUP_DIR.toString()));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM houses", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batches", Long.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_records", Long.class)).isGreaterThanOrEqualTo(8_000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_usage", Long.class)).isGreaterThanOrEqualTo(6_000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM egg_collections", Long.class)).isGreaterThanOrEqualTo(6_000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales", Long.class)).isGreaterThanOrEqualTo(400);

        // Generous CI-safe ceilings. The test is intended to catch accidental
        // O(n^2) query regressions, not to certify a particular machine speed.
        assertThat(dashboardMs).isLessThan(5_000);
        assertThat(layerBatchMs).isLessThan(3_000);
        assertThat(broilerBatchMs).isLessThan(3_000);
        assertThat(reportMs).isLessThan(5_000);
        assertThat(filteredSearchMs).isLessThan(1_500);
        assertThat(customerSearchMs).isLessThan(500);
        assertThat(backupMs).isLessThan(10_000);

        assertIndexed("bird_population_events", "batch_id = ? AND event_type = ? AND event_date <= ?");
        assertIndexed("egg_collections", "batch_id = ? AND record_date <= ?");
        assertIndexed("sales", "farm_id = ? AND sale_date <= ?");
        assertIndexed("expenses", "farm_id = ? AND occurred_date <= ?");
        assertIndexed("feed_usage", "feed_type_id = ? AND usage_date <= ?");
        assertIndexed("daily_records", "batch_id = ? AND record_date = ?");

        List<Path> backupFiles;
        try (var paths = Files.list(BACKUP_DIR)) {
            backupFiles = paths.toList();
        }
        assertThat(backupFiles).isNotEmpty();

        Path backup = backupFiles.get(0);
        long restoreValidationMs = timed(() -> backups.validate(backup.toString()));
        assertThat(restoreValidationMs).isLessThan(5_000);
    }

    private void seedDataset() {
        String now = "2025-12-31T23:59:00Z";
        farmId = uuid();
        jdbc.update("""
                INSERT INTO farms(id,name,location,timezone,currency,status,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?)
                """, farmId, "Phase 35 Performance Farm", "Port Harcourt",
                "Africa/Lagos", "NGN", "ACTIVE", now, now);

        String[] houses = new String[4];
        for (int i = 0; i < houses.length; i++) {
            houses[i] = uuid();
            jdbc.update("""
                    INSERT INTO houses(id,farm_id,name,code,status,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?)
                    """, houses[i], farmId, "House " + (i + 1), "H" + (i + 1),
                    "ACTIVE", now, now);
        }

        String[] batches = new String[8];
        for (int i = 0; i < batches.length; i++) {
            batches[i] = uuid();
            String type = i < 4 ? "LAYER" : "BROILER";
            jdbc.update("""
                    INSERT INTO batches(
                        id,farm_id,house_id,code,batch_type,placement_date,
                        initial_bird_count,status,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?)
                    """, batches[i], farmId, houses[i % 4],
                    type.substring(0, 1) + "-2023-" + String.format("%03d", i + 1),
                    type, START.toString(), 5_000, "ACTIVE", now, now);
        }
        layerBatchId = batches[0];
        broilerBatchId = batches[4];

        String[] feedTypes = new String[4];
        for (int i = 0; i < feedTypes.length; i++) {
            feedTypes[i] = uuid();
            jdbc.update("""
                    INSERT INTO feed_types(
                        id,farm_id,name,unit,applicable_type,status,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?,?)
                    """, feedTypes[i], farmId, "Feed " + (i + 1), "kg",
                    i < 2 ? "LAYER" : "BROILER", "ACTIVE", now, now);
        }

        for (int i = 0; i < feedTypes.length; i++) {
            for (int p = 0; p < 12; p++) {
                LocalDate date = START.plusMonths(p * 3L);
                jdbc.update("""
                        INSERT INTO feed_purchases(
                            id,farm_id,feed_type_id,purchase_date,quantity_milli,
                            unit,total_cost_minor,created_at)
                        VALUES(?,?,?,?,?,?,?,?)
                        """, uuid(), farmId, feedTypes[i], date.toString(),
                        65_000_000L, "kg", 32_500_000L, now);
            }
        }

        jdbc.batchUpdate("""
                INSERT INTO daily_records(id,batch_id,record_date,notes,created_at,updated_at)
                VALUES(?,?,?,?,?,?)
                """, jdbc.getDataSource() == null ? List.of() : buildDailyRows(batches, now));

        List<Object[]> feedRows = new java.util.ArrayList<>();
        for (int i = 0; i < 6_000; i++) {
            int batchIndex = i % batches.length;
            String feedType = feedTypes[batchIndex / 2];
            LocalDate date = START.plusDays(i % 1_095L);
            feedRows.add(new Object[]{
                    uuid(), batches[batchIndex], feedType, date.toString(),
                    500_000L, "Production feed", now
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO feed_usage(
                    id,batch_id,feed_type_id,usage_date,quantity_milli,reason,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, feedRows);

        List<Object[]> eggRows = new java.util.ArrayList<>();
        for (int i = 0; i < 6_000; i++) {
            String batch = batches[i % 4];
            LocalDate date = START.plusDays(i % 1_095L);
            eggRows.add(new Object[]{
                    uuid(), batch, date.toString(), 400, 8, "Performance seed", now
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO egg_collections(
                    id,batch_id,record_date,good_eggs,cracked_eggs,notes,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, eggRows);

        List<Object[]> weightRows = new java.util.ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            String batch = batches[4 + (i % 4)];
            LocalDate date = START.plusDays(i % 1_095L);
            weightRows.add(new Object[]{
                    uuid(), batch, date.toString(), 20, 40_000L, "Performance seed", now
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO weight_records(
                    id,batch_id,record_date,sample_quantity,total_weight_grams,notes,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, weightRows);

        for (int i = 0; i < 8; i++) {
            jdbc.update("""
                    INSERT INTO bird_population_events(
                        id,batch_id,event_date,event_type,quantity,reason,notes,created_at)
                    VALUES(?,?,?,?,?,?,?,?)
                    """, uuid(), batches[i], START.plusDays(365).toString(),
                    "MORTALITY", 100, "Performance seed", null, now);
        }

        customerId = uuid();
        for (int i = 0; i < 20; i++) {
            String id = i == 4 ? customerId : uuid();
            jdbc.update("""
                    INSERT INTO customers(id,farm_id,name,phone,notes,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?)
                    """, id, farmId, String.format("Customer %03d", i),
                    "08000000000", null, now, now);
        }

        List<Object[]> salesRows = new java.util.ArrayList<>();
        for (int i = 0; i < 400; i++) {
            String batch = batches[i % 8];
            String type = i % 2 == 0 ? "EGG" : "BROILER";
            String customer = i % 20 == 4 ? customerId : jdbc.queryForObject(
                    "SELECT id FROM customers WHERE farm_id=? ORDER BY name LIMIT 1 OFFSET ?",
                    String.class, farmId, i % 20);
            salesRows.add(new Object[]{
                    uuid(), farmId, batch, customer,
                    START.plusDays(i % 1_095L).toString(), type,
                    BigDecimal.valueOf(10), type.equals("EGG") ? "CRATE" : "BIRD",
                    50_000L, 500_000L, type + "_SALE", uuid(), now
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO sales(
                    id,farm_id,batch_id,customer_id,sale_date,sale_type,quantity,
                    unit,unit_price_minor,total_amount_minor,reference_type,reference_id,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, salesRows);

        List<Object[]> expenseRows = new java.util.ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            expenseRows.add(new Object[]{
                    uuid(), farmId, "OTHER", 10_000L,
                    START.plusDays(i % 1_095L).toString(),
                    null, null, "Performance expense", now
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO expenses(
                    id,farm_id,category,amount_minor,occurred_date,
                    reference_type,reference_id,description,created_at)
                VALUES(?,?,?,?,?,?,?,?,?)
                """, expenseRows);
    }

    private List<Object[]> buildDailyRows(String[] batches, String now) {
        List<Object[]> rows = new java.util.ArrayList<>(8_760);
        for (String batch : batches) {
            for (int day = 0; day < 1_095; day++) {
                LocalDate date = START.plusDays(day);
                rows.add(new Object[]{
                        uuid(), batch, date.toString(), "Performance seed", now, now
                });
            }
        }
        return rows;
    }

    private void assertIndexed(String table, String predicate) {
        String sql = switch (table) {
            case "bird_population_events" ->
                    "EXPLAIN QUERY PLAN SELECT quantity FROM bird_population_events WHERE batch_id=? AND event_type=? AND event_date<=?";
            case "egg_collections" ->
                    "EXPLAIN QUERY PLAN SELECT good_eggs, cracked_eggs FROM egg_collections WHERE batch_id=? AND record_date<=?";
            case "sales" ->
                    "EXPLAIN QUERY PLAN SELECT total_amount_minor FROM sales WHERE farm_id=? AND sale_date<=?";
            case "expenses" ->
                    "EXPLAIN QUERY PLAN SELECT amount_minor FROM expenses WHERE farm_id=? AND occurred_date<=?";
            case "feed_usage" ->
                    "EXPLAIN QUERY PLAN SELECT quantity_milli FROM feed_usage WHERE feed_type_id=? AND usage_date<=?";
            case "daily_records" ->
                    "EXPLAIN QUERY PLAN SELECT id FROM daily_records WHERE batch_id=? AND record_date=?";
            default -> throw new IllegalArgumentException("Unsupported performance query: " + table);
        };
        String plan = jdbc.query(sql, rs -> {
            StringBuilder value = new StringBuilder();
            while (rs.next()) value.append(rs.getString("detail")).append('\n');
            return value.toString();
        });
        assertThat(plan.toUpperCase())
                .as("Expected an index for %s predicate %s", table, predicate)
                .contains("USING")
                .contains("INDEX");
    }

    private long timed(Runnable operation) {
        long start = System.nanoTime();
        operation.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }

    @AfterAll
    static void reportFiles() {
        // Keep the performance database out of source control. .gitignore already
        // excludes target/ and database artifacts.
    }
}
