package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.ApplicationSettingsRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationSettingsRequestTest {
    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void acceptsEveryPageOfferedInSettings() {
        for (String page : Set.of("dashboard", "flocks", "daily-work", "feed-stock",
                "health", "production", "sales-money", "reports", "settings")) {
            var violations = validator.validate(new ApplicationSettingsRequest(page, "YYYY-MM-DD"));
            assertThat(violations).as("start page %s should be supported", page).isEmpty();
        }
    }

    @Test
    void rejectsUnsupportedStartPagesAndDateFormats() {
        var violations = validator.validate(new ApplicationSettingsRequest("unknown-page", "MM/DD/YYYY"));
        assertThat(violations).hasSize(2);
        assertThat(violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("startPage", "dateFormat");
    }
}
