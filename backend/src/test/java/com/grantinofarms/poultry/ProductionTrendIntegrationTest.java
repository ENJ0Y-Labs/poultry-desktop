package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.BatchCreateRequest;
import com.grantinofarms.poultry.dto.FarmCreateRequest;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.HouseService;
import com.grantinofarms.poultry.service.ProductionTrendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:production-trends-integration?mode=memory&cache=shared",
        "poultry.database-path=:memory:",
        "poultry.auth-required=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProductionTrendIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired ProductionTrendService trends;

    @BeforeEach
    void setUpFarm() {
        farmService.create(new FarmCreateRequest("Trend Test Farm", "Port Harcourt", "Africa/Lagos", "NGN"));
        houseService.create(new HouseCreateRequest("Test House", "TH1", null));
    }

    @Test
    void layerHdpUsesBackendPopulationAndLeavesMissingProductionDaysAsGaps() {
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 10, 1), firstHouseId(), 100, null, 100_000L));
        jdbc.update("""
                INSERT INTO egg_collections
                    (id, batch_id, record_date, good_eggs, cracked_eggs, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "egg-1", batch.id(), "2026-10-01", 9, 1, null, "2026-10-01T08:00:00Z");
        jdbc.update("""
                INSERT INTO bird_population_events
                    (id, batch_id, event_date, event_type, quantity, reference_batch_id, reason, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "mortality-1", batch.id(), "2026-10-02", "MORTALITY", 10, null, null, null, "2026-10-02T08:00:00Z");
        jdbc.update("""
                INSERT INTO egg_collections
                    (id, batch_id, record_date, good_eggs, cracked_eggs, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "egg-3", batch.id(), "2026-10-03", 45, 0, null, "2026-10-03T08:00:00Z");

        Map<String, Object> trend = trends.layerHdp(batch.id(),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) trend.get("points");

        assertThat(trend).containsEntry("metric", "HDP%").containsEntry("unit", "percent");
        assertThat(points).hasSize(3);
        assertThat(points.get(0)).containsEntry("value", new java.math.BigDecimal("10.00"))
                .containsEntry("recorded", true).containsEntry("liveBirds", 100);
        assertThat(points.get(1)).containsEntry("value", null).containsEntry("recorded", false)
                .containsEntry("liveBirds", 90);
        assertThat(points.get(2)).containsEntry("value", new java.math.BigDecimal("50.00"))
                .containsEntry("recorded", true).containsEntry("liveBirds", 90);
    }

    @Test
    void broilerWeightTrendAggregatesSamplesPerDayAndLeavesMissingDaysAsGaps() {
        var batch = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 10, 1), firstHouseId(), 50, null, 100_000L));
        jdbc.update("""
                INSERT INTO weight_records
                    (id, batch_id, record_date, sample_quantity, total_weight_grams, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "weight-1", batch.id(), "2026-10-01", 2, 2_000L, null, "2026-10-01T08:00:00Z");
        jdbc.update("""
                INSERT INTO weight_records
                    (id, batch_id, record_date, sample_quantity, total_weight_grams, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "weight-2", batch.id(), "2026-10-01", 2, 3_000L, null, "2026-10-01T09:00:00Z");
        Map<String, Object> trend = trends.broilerAverageWeight(batch.id(),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) trend.get("points");

        assertThat(trend).containsEntry("metric", "Average weight").containsEntry("unit", "kg");
        assertThat(points).hasSize(2);
        assertThat(points.get(0)).containsEntry("value", new java.math.BigDecimal("1.250"))
                .containsEntry("recorded", true);
        assertThat(points.get(1)).containsEntry("value", null).containsEntry("recorded", false);
    }

    @Test
    void trendEndpointsRejectIncompatibleBatchTypesAndDatesBeforePlacement() {
        var layer = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 10, 3), firstHouseId(), 20, null, 20_000L));
        assertThatThrownBy(() -> trends.broilerAverageWeight(layer.id(),
                LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> trends.layerHdp(layer.id(),
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 4)))
                .isInstanceOf(ApiException.class);
    }

    private String firstHouseId() {
        return jdbc.queryForObject("SELECT id FROM houses LIMIT 1", String.class);
    }
}
