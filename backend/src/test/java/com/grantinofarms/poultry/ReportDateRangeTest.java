package com.grantinofarms.poultry;

import com.grantinofarms.poultry.domain.ReportDateRange;
import com.grantinofarms.poultry.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportDateRangeTest {
    @Test
    void includesBothBoundaryDates() {
        ReportDateRange range = ReportDateRange.require(
                LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 1));

        assertThat(range.includes(LocalDate.of(2024, 2, 29))).isTrue();
        assertThat(range.includes(LocalDate.of(2024, 3, 1))).isTrue();
        assertThat(range.includes(LocalDate.of(2024, 2, 28))).isFalse();
        assertThat(range.includes(LocalDate.of(2024, 3, 2))).isFalse();
    }

    @Test
    void rejectsMissingOrReversedBoundaries() {
        assertThatThrownBy(() -> ReportDateRange.require(null, LocalDate.now()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Both start and end dates are required");
        assertThatThrownBy(() -> ReportDateRange.require(
                LocalDate.of(2024, 3, 2), LocalDate.of(2024, 3, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("on or before");
    }
}
