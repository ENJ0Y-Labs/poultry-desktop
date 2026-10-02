package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.FarmCreateRequest;
import com.grantinofarms.poultry.dto.FarmSettingsRequest;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.HouseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:farm-management-test?mode=memory&cache=shared"
})
class FarmManagementIntegrationTest {

    @Autowired FarmService farmService;
    @Autowired HouseService houseService;

    @Test
    @Transactional
    void createsFarmSettingsAndHouse() {
        var farm = farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));

        assertThat(farm.name()).isEqualTo("Grantino Farms");
        assertThat(farm.currency()).isEqualTo("NGN");

        var settings = farmService.updateSettings(new FarmSettingsRequest(
                30, 75, List.of(25, 75)));

        assertThat(settings.defaultCrateSize()).isEqualTo(30);
        assertThat(settings.defaultWaterContainerSize()).isEqualTo(75);
        assertThat(settings.waterContainerSizes()).containsExactly(25, 75);

        var house = houseService.create(new HouseCreateRequest(
                "Layer House 1", "LH1", "North block"));

        assertThat(house.farmId()).isEqualTo(farm.id());
        assertThat(house.status()).isEqualTo("ACTIVE");
        assertThat(houseService.list()).hasSize(1);
    }
}
