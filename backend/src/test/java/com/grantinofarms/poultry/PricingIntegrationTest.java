package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:pricing-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PricingIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired PricingService pricingService;
    @Autowired BroilerProductionService broilerService;

    @Test
    void calculatesPriceFromActualBirdCostAndConfiguredTarget() {
        var batch = setupBroiler();

        assertThat(pricingService.settings().targetMarginPercent()).isNull();
        assertThat(pricingService.settings().workingMarginPercent()).isNull();

        var settings = pricingService.updateSettings(new PricingSettingsRequest(new BigDecimal("20")));
        assertThat(settings.targetMarginPercent()).isEqualByComparingTo("20");

        var price = pricingService.price(batch.id(), 10, LocalDate.of(2026, 1, 10));
        assertThat(price.actualCostMinor()).isEqualTo(100_000L);
        assertThat(price.targetPricePerBirdMinor()).isEqualTo(12_500L);
    }

    @Test
    void belowTargetSaleRequiresConfirmationAndDoesNotLowerTarget() {
        var batch = setupBroiler();
        pricingService.updateSettings(new PricingSettingsRequest(new BigDecimal("20")));

        assertThatThrownBy(() -> broilerService.addSale(batch.id(), new BirdSaleRequest(
                LocalDate.of(2026, 1, 10), 10, 8_000L, "Low-price customer")))
                .hasMessage("This sale is below the configured target margin. Confirmation is required.");

        assertThat(pricingService.settings().targetMarginPercent()).isEqualByComparingTo("20");
        assertThat(pricingService.settings().workingMarginPercent()).isNull();
    }

    @Test
    void saleAboveTargetCanIncreaseWorkingMargin() {
        var batch = setupBroiler();
        pricingService.updateSettings(new PricingSettingsRequest(new BigDecimal("20")));

        broilerService.addSale(batch.id(), new BirdSaleRequest(
                LocalDate.of(2026, 1, 10), 10, 15_000L, "Good customer"));

        assertThat(pricingService.settings().targetMarginPercent()).isEqualByComparingTo("20");
        assertThat(pricingService.settings().workingMarginPercent()).isEqualByComparingTo("33.333333");
    }

    private BatchResponse setupBroiler() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest("Broiler House", "BH1", null));
        return batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));
    }
}
