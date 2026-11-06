package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.BatchCreateRequest;
import com.grantinofarms.poultry.dto.FarmCreateRequest;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.dto.HouseUpdateRequest;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.DashboardService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.HouseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:house-classification-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HouseClassificationIntegrationTest {
    private static final LocalDate PLACED = LocalDate.of(2026, 11, 6);

    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired DashboardService dashboardService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void sevenHouseSetupCountsTypeAndOccupancyFromActiveBatchAssignments() {
        farmService.create(new FarmCreateRequest("Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var h001 = houseService.create(new HouseCreateRequest("House 001", "H001", null, "PERMANENT"));
        var h002 = houseService.create(new HouseCreateRequest("House 002", "H002", null, "PERMANENT"));
        var h003 = houseService.create(new HouseCreateRequest("House 003", "H003", null, "PERMANENT"));
        var h004 = houseService.create(new HouseCreateRequest("House 004", "H004", null, "PERMANENT"));
        var t001 = houseService.create(new HouseCreateRequest("Temporary 001", "T001", null, "TEMPORARY"));
        var t002 = houseService.create(new HouseCreateRequest("Temporary 002", "T002", null, "TEMPORARY"));
        var t003 = houseService.create(new HouseCreateRequest("Temporary 003", "T003", null, "TEMPORARY"));

        for (var house : new Object[][]{{h001, 100}, {h002, 100}, {h004, 100}, {t002, 100}, {t003, 100}}) {
            var response = (com.grantinofarms.poultry.dto.HouseResponse) house[0];
            batchService.create(new BatchCreateRequest("LAYER", PLACED, response.id(), (int) house[1], null, false, null));
        }

        Map<String, Object> dashboard = dashboardService.farm(PLACED);
        assertThat(((Number) dashboard.get("totalHouses")).intValue()).isEqualTo(7);
        assertThat(((Number) dashboard.get("permanentHouses")).intValue()).isEqualTo(4);
        assertThat(((Number) dashboard.get("temporaryHouses")).intValue()).isEqualTo(3);
        assertThat(((Number) dashboard.get("occupiedHouses")).intValue()).isEqualTo(5);
        assertThat(((Number) dashboard.get("emptyHouses")).intValue()).isEqualTo(2);

        assertThat(houseService.list()).extracting("houseType")
                .containsExactlyInAnyOrder("PERMANENT", "PERMANENT", "PERMANENT", "PERMANENT", "TEMPORARY", "TEMPORARY", "TEMPORARY");
        assertThat(houseService.list()).extracting("code")
                .contains("H001", "H002", "H003", "H004", "T001", "T002", "T003");

        // A closed historical batch no longer occupies its house; the house remains reusable.
        jdbc.update("UPDATE batches SET status='CLOSED' WHERE house_id=?", h001.id());
        dashboard = dashboardService.farm(PLACED);
        assertThat(((Number) dashboard.get("occupiedHouses")).intValue()).isEqualTo(4);
        assertThat(((Number) dashboard.get("emptyHouses")).intValue()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batches WHERE house_id=?", Integer.class, h001.id())).isEqualTo(1);
    }

    @Test
    void houseTypeCanBeChangedAndChangeIsAuditedWithoutChangingHouseCodeOrAssignments() {
        farmService.create(new FarmCreateRequest("Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest("Temporary House", "H001", "Existing notes", "TEMPORARY"));
        var batch = batchService.create(new BatchCreateRequest("LAYER", PLACED, house.id(), 25, null, false, null));

        var updated = houseService.update(house.id(), new HouseUpdateRequest(
                "Permanent House", "H001", "Updated notes", "ACTIVE", "PERMANENT"));
        assertThat(updated.houseType()).isEqualTo("PERMANENT");
        assertThat(updated.code()).isEqualTo("H001");
        assertThat(batchService.list()).extracting("id").contains(batch.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type='HOUSE' AND entity_id=? AND action='UPDATE'", Integer.class, house.id())).isEqualTo(1);
    }

    @Test
    void legacyHouseMigrationDefaultsExplicitlyWithoutInspectingCodePrefix() {
        String farmId = UUID.randomUUID().toString();
        String now = "2026-11-06T00:00:00Z";
        jdbc.update("INSERT INTO farms (id,name,timezone,currency,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                farmId, "Legacy Farm", "Africa/Lagos", "NGN", "ACTIVE", now, now);
        String houseId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO houses (id,farm_id,name,code,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                houseId, farmId, "Legacy temporary house", "T999", "ACTIVE", now, now);
        assertThat(jdbc.queryForObject("SELECT house_type FROM houses WHERE id=?", String.class, houseId)).isEqualTo("PERMANENT");
        assertThatThrownBy(() -> jdbc.update("UPDATE houses SET house_type='UNKNOWN' WHERE id=?", houseId))
                .isInstanceOf(Exception.class);
    }
}
