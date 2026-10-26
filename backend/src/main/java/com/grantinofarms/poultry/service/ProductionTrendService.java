package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ProductionTrendService {
    private static final int MAX_DAYS = 366;
    private final JdbcTemplate jdbc;
    private final BirdPopulationService population;

    public ProductionTrendService(JdbcTemplate jdbc, BirdPopulationService population) {
        this.jdbc = jdbc;
        this.population = population;
    }

    public Map<String, Object> layerHdp(String batchId, LocalDate from, LocalDate to) {
        BatchInfo batch = requireBatch(batchId);
        if (!"LAYER".equals(batch.type())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INCOMPATIBLE_BATCH_TYPE",
                    "Layer HDP% trends are only available for LAYER batches.");
        }
        DateRange range = range(batch.placementDate(), from, to);
        Map<LocalDate, Integer> eggsByDate = new TreeMap<>();
        jdbc.query("""
                SELECT record_date, SUM(good_eggs + cracked_eggs) AS eggs
                FROM egg_collections
                WHERE batch_id = ? AND record_date BETWEEN ? AND ?
                GROUP BY record_date
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    eggsByDate.put(LocalDate.parse(rs.getString("record_date")), rs.getInt("eggs"));
                }, batchId, range.from().toString(), range.to().toString());

        List<Map<String, Object>> points = new ArrayList<>();
        for (LocalDate date = range.from(); !date.isAfter(range.to()); date = date.plusDays(1)) {
            Integer eggs = eggsByDate.get(date);
            Integer liveBirds = population.get(batchId, date).currentBirds();
            BigDecimal value = eggs == null || liveBirds == null || liveBirds <= 0
                    ? null
                    : BigDecimal.valueOf(eggs).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(liveBirds), 2, RoundingMode.HALF_UP);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date);
            point.put("value", value);
            point.put("recorded", eggs != null);
            point.put("eggs", eggs);
            point.put("liveBirds", liveBirds);
            points.add(point);
        }
        return response(batchId, "LAYER", "HDP%", "percent", range, points);
    }

    public Map<String, Object> broilerAverageWeight(String batchId, LocalDate from, LocalDate to) {
        BatchInfo batch = requireBatch(batchId);
        if (!"BROILER".equals(batch.type())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INCOMPATIBLE_BATCH_TYPE",
                    "Average-weight trends are only available for BROILER batches.");
        }
        DateRange range = range(batch.placementDate(), from, to);
        Map<LocalDate, BigDecimal> averageByDate = new TreeMap<>();
        jdbc.query("""
                SELECT record_date,
                       SUM(total_weight_grams) AS total_grams,
                       SUM(sample_quantity) AS sampled_birds
                FROM weight_records
                WHERE batch_id = ? AND record_date BETWEEN ? AND ?
                GROUP BY record_date
                """, rs -> {
            long grams = rs.getLong("total_grams");
            int sampled = rs.getInt("sampled_birds");
            BigDecimal kg = BigDecimal.valueOf(grams).movePointLeft(3)
                    .divide(BigDecimal.valueOf(sampled), 3, RoundingMode.HALF_UP);
            averageByDate.put(LocalDate.parse(rs.getString("record_date")), kg);
        }, batchId, range.from().toString(), range.to().toString());

        List<Map<String, Object>> points = new ArrayList<>();
        for (LocalDate date = range.from(); !date.isAfter(range.to()); date = date.plusDays(1)) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date);
            point.put("value", averageByDate.get(date));
            point.put("recorded", averageByDate.containsKey(date));
            points.add(point);
        }
        return response(batchId, "BROILER", "Average weight", "kg", range, points);
    }

    private BatchInfo requireBatch(String batchId) {
        List<BatchInfo> batches = jdbc.query("""
                SELECT batch_type, placement_date FROM batches WHERE id = ?
                """, (rs, row) -> new BatchInfo(rs.getString("batch_type"),
                LocalDate.parse(rs.getString("placement_date"))), batchId);
        if (batches.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "The requested batch was not found.");
        }
        return batches.get(0);
    }

    private DateRange range(LocalDate placementDate, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? (end.minusDays(29).isBefore(placementDate) ? placementDate : end.minusDays(29)) : from;
        if (start.isAfter(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "The start date must be on or before the end date.");
        }
        if (start.isBefore(placementDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_BEFORE_PLACEMENT", "Trend dates cannot be before the batch placement date.");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_RANGE_TOO_LARGE", "Trend ranges cannot exceed 366 days.");
        }
        return new DateRange(start, end);
    }

    private Map<String, Object> response(String batchId, String batchType, String metric, String unit,
                                         DateRange range, List<Map<String, Object>> points) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("batchType", batchType);
        result.put("metric", metric);
        result.put("unit", unit);
        result.put("from", range.from());
        result.put("to", range.to());
        result.put("missingDayPolicy", "value=null when no production record exists; gaps are not treated as zero");
        result.put("points", points);
        return result;
    }

    private record BatchInfo(String type, LocalDate placementDate) {}
    private record DateRange(LocalDate from, LocalDate to) {}
}
