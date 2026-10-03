package com.grantinofarms.poultry;

import com.grantinofarms.poultry.service.DashboardService;
import com.grantinofarms.poultry.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    @Test
    void farmReportOnlyIncludesSalesAndExpensesOnOrBeforeAsOfDate() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite::memory:");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE sales (
                    sale_date TEXT,
                    sale_type TEXT,
                    quantity NUMERIC,
                    unit TEXT,
                    total_amount_minor INTEGER
                )
                """);
        jdbc.execute("""
                CREATE TABLE expenses (
                    occurred_date TEXT,
                    category TEXT,
                    description TEXT,
                    amount_minor INTEGER,
                    batch_id TEXT
                )
                """);

        jdbc.update("""
                INSERT INTO sales VALUES
                ('2026-01-05', 'EGG', 1, 'CRATE', 300000),
                ('2026-01-20', 'EGG', 1, 'CRATE', 500000)
                """);
        jdbc.update("""
                INSERT INTO expenses VALUES
                ('2026-01-04', 'FEED', 'Starter feed', 100000, 'batch-1'),
                ('2026-01-21', 'FEED', 'Later feed', 200000, 'batch-1')
                """);

        var dashboard = mock(DashboardService.class);
        when(dashboard.farm(LocalDate.of(2026, 1, 10))).thenReturn(
                Map.of("asOf", "2026-01-10", "profitMinor", 200000L)
        );

        var report = new ReportService(dashboard, jdbc).farm(LocalDate.of(2026, 1, 10));

        assertThat(report.get("asOf")).isEqualTo(LocalDate.of(2026, 1, 10));

        var sales = (java.util.List<?>) report.get("sales");
        assertThat(sales).hasSize(1);

        var expenses = (java.util.List<?>) report.get("expenses");
        assertThat(expenses).hasSize(1);
    }
}
