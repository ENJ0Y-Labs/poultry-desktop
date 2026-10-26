package com.grantinofarms.poultry.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationSettingsRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private static final List<String> SUPPORTED_START_PAGES = List.of(
            "dashboard", "flocks", "daily-work", "feed-stock", "health",
            "production", "sales-money", "reports", "settings"
    );

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsEveryStartPageOfferedBySettings() {
        for (String startPage : SUPPORTED_START_PAGES) {
            var request = new ApplicationSettingsRequest(startPage, "YYYY-MM-DD");

            assertThat(validator.validate(request))
                    .as("start page '%s' should be accepted", startPage)
                    .isEmpty();
        }
    }

    @Test
    void rejectsUnsupportedAndLegacyStartPageValues() {
        for (String startPage : List.of("operations", "overview", "not-a-page", "")) {
            var request = new ApplicationSettingsRequest(startPage, "YYYY-MM-DD");

            assertThat(validator.validate(request))
                    .as("start page '%s' should be rejected", startPage)
                    .isNotEmpty();
        }
    }
}
