package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:turkey-support-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TurkeySupportIntegrationTest {
    private static final LocalDate PLACED = LocalDate.of(2026, 6, 1);
    private static final LocalDate ACTIVITY = LocalDate.of(2026, 6, 8);

    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired BirdPopulationService populationService;
    @Autowired FeedService feedService;
    @Autowired DailyOperationsService dailyService;
    @Autowired HealthManagementService healthService;
    @Autowired BroilerProductionService meatProductionService;
    @Autowired EggManagementService eggService;
    @Autowired DashboardService dashboardService;
    @Autowired ReportService reportService;

    @Test
    void turkeyIsIndependentAcrossOperationsDashboardAndReports() {
        farmService.create(new FarmCreateRequest("Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest("Turkey House", "TH1", null));
        var turkey = batchService.create(new BatchCreateRequest(
                "TURKEY", PLACED, house.id(), 20, null, 200_000L));
        var broiler = batchService.create(new BatchCreateRequest(
                "BROILER", PLACED, house.id(), 30, null, 300_000L));
        var layer = batchService.create(new BatchCreateRequest(
                "LAYER", PLACED, house.id(), 40, null, 400_000L));

        assertThat(turkey.code()).startsWith("TY-");
        assertThat(turkey.type()).isEqualTo("TURKEY");

        populationService.addMortality(turkey.id(), new BirdPopulationEventRequest(
                ACTIVITY, 2, "Mortality", "Turkey mortality test"));
        populationService.addCulling(turkey.id(), new BirdPopulationEventRequest(
                ACTIVITY, 1, "Culling", "Turkey culling test"));

        var feedType = feedService.createType(new FeedTypeCreateRequest(
                "Turkey Grower", "kg", "TURKEY"));
        feedService.purchase(new FeedPurchaseRequest(
                feedType.id(), null, PLACED, new BigDecimal("10"), "kg", 50_000L));
        var feedUse = feedService.use(turkey.id(), new FeedUsageRequest(
                feedType.id(), ACTIVITY, new BigDecimal("2"), "kg"));
        assertThat(feedUse.quantityMilli()).isEqualTo(2_000L);

        var daily = dailyService.create(turkey.id(), new DailyRecordRequest(
                ACTIVITY, "Turkey daily record", null));
        assertThat(daily.notes()).isEqualTo("Turkey daily record");

        var health = healthService.addHealth(turkey.id(), new HealthRecordRequest(
                ACTIVITY, "Observation", "Turkey health observation", "Monitor flock"));
        var drug = healthService.addDrug(turkey.id(), new DrugRecordRequest(
                ACTIVITY, "Turkey medicine", 1, 5_000L, "Treatment"));
        var vaccination = healthService.addVaccination(turkey.id(), new VaccinationRecordRequest(
                ACTIVITY, "Turkey vaccine", "1 dose", 17, "Routine vaccination"));
        assertThat(healthService.health(turkey.id())).extracting(HealthRecordResponse::id).contains(health.id());
        assertThat(healthService.drugs(turkey.id())).extracting(DrugRecordResponse::id).contains(drug.id());
        assertThat(healthService.vaccinations(turkey.id()))
                .extracting(VaccinationRecordResponse::id).contains(vaccination.id());

        var weight = meatProductionService.addWeight(turkey.id(), new WeightRecordRequest(
                ACTIVITY, 5, new BigDecimal("12.500"), "Turkey sample"));
        assertThat(weight.averageWeightKg()).isEqualByComparingTo("2.5");
        var sale = meatProductionService.addSale(turkey.id(), new BirdSaleRequest(
                ACTIVITY, 2, 15_000L, "Turkey buyer"));
        assertThat(sale.quantity()).isEqualTo(2);

        assertThatThrownBy(() -> eggService.addCollection(turkey.id(), new EggCollectionRequest(
                ACTIVITY, 1, 0, "Must be rejected")))
                .hasMessage("Egg production is only available for LAYER batches.");

        Map<String, Object> farmDashboard = dashboardService.farm(ACTIVITY);
        assertThat(((Number) farmDashboard.get("totalBirds")).longValue()).isEqualTo(87);
        assertThat(((Number) farmDashboard.get("layerBirds")).longValue()).isEqualTo(40);
        assertThat(((Number) farmDashboard.get("broilerBirds")).longValue()).isEqualTo(30);
        assertThat(((Number) farmDashboard.get("turkeyBirds")).longValue()).isEqualTo(15);

        Map<String, Object> farmReport = reportService.farm(ACTIVITY);
        List<Map<String, Object>> turkeyGrowth = (List<Map<String, Object>>) farmReport.get("turkeyGrowth");
        List<Map<String, Object>> broilerGrowth = (List<Map<String, Object>>) farmReport.get("broilerGrowth");
        List<Map<String, Object>> meatBirdGrowth = (List<Map<String, Object>>) farmReport.get("meatBirdGrowth");
        assertThat(turkeyGrowth).hasSize(1);
        assertThat(((BatchResponse) turkeyGrowth.get(0).get("batch")).id()).isEqualTo(turkey.id());
        assertThat(broilerGrowth).hasSize(1);
        assertThat(((BatchResponse) broilerGrowth.get(0).get("batch")).id()).isEqualTo(broiler.id());
        assertThat(meatBirdGrowth).hasSize(2);
        assertThat(farmReport.get("eggProduction")).asList()
                .extracting(row -> ((BatchResponse) ((Map<?, ?>) row).get("batch")).id())
                .containsExactly(layer.id());
    }

    @Test
    void turkeyBatchReportsExposeTurkeyProductionAndHealthHistory() {
        farmService.create(new FarmCreateRequest("Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest("Turkey House", "TH1", null));
        var turkey = batchService.create(new BatchCreateRequest(
                "TURKEY", PLACED, house.id(), 12, null, 120_000L));
        meatProductionService.addWeight(turkey.id(), new WeightRecordRequest(
                ACTIVITY, 4, new BigDecimal("8.000"), "Weekly turkey weight"));

        Map<String, Object> report = reportService.batch(turkey.id(), ACTIVITY);
        Map<?, ?> production = (Map<?, ?>) report.get("production");
        assertThat(production.get("type")).isEqualTo("TURKEY");
        assertThat(production.get("weights")).asList().hasSize(1);
        assertThat(report.get("health")).isInstanceOf(Map.class);
    }
}
