package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.ApplicationSettingsRequest;
import com.grantinofarms.poultry.dto.BackupSettingsRequest;
import com.grantinofarms.poultry.dto.ExpenseCategoryRequest;
import com.grantinofarms.poultry.dto.FarmCreateRequest;
import com.grantinofarms.poultry.service.ApplicationSettingsService;
import com.grantinofarms.poultry.service.BackupSettingsService;
import com.grantinofarms.poultry.service.ExpenseCategoryService;
import com.grantinofarms.poultry.service.ExpenseService;
import com.grantinofarms.poultry.service.FarmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:settings-management-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SettingsManagementIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired ApplicationSettingsService applicationSettings;
    @Autowired BackupSettingsService backupSettings;
    @Autowired ExpenseCategoryService expenseCategories;
    @Autowired ExpenseService expenses;

    @Test
    void createsAndUpdatesAllFarmSettings() {
        farmService.create(new FarmCreateRequest("Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));

        assertThat(applicationSettings.get()).containsEntry("startPage", "dashboard");
        assertThat(backupSettings.get()).containsEntry("enabled", false);
        assertThat(expenseCategories.list()).extracting("name").contains("FEED", "DRUGS", "OTHER");

        assertThat(applicationSettings.update(new ApplicationSettingsRequest("reports", "DD/MM/YYYY")))
                .containsEntry("startPage", "reports");
        assertThat(backupSettings.update(new BackupSettingsRequest(true, "D:/GrantinoBackups", 21600000L)))
                .containsEntry("enabled", true);
    }

    @Test
    void customExpenseCategoriesAreValidatedAndAudited() {
        farmService.create(new FarmCreateRequest("Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var created = expenseCategories.create(new ExpenseCategoryRequest("Utilities"));

        assertThat(created.get("name")).isEqualTo("Utilities");
        assertThat(expenseCategories.list()).extracting("name").contains("Utilities");

        var expense = expenses.create(new com.grantinofarms.poultry.dto.ExpenseRequest(
                LocalDate.of(2026, 10, 3), "Generator fuel", 15000L, "Utilities", null));
        assertThat(expense.category()).isEqualTo("Utilities");

        expenseCategories.archive(created.get("id"));
        assertThatThrownBy(() -> expenses.create(new com.grantinofarms.poultry.dto.ExpenseRequest(
                LocalDate.of(2026, 10, 3), "Another", 1000L, "Utilities", null)))
                .hasMessage("The expense category is not active for this farm.");
    }
}
