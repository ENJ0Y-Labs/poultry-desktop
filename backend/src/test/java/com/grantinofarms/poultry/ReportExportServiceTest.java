package com.grantinofarms.poultry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.service.ReportExportService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExportServiceTest {

    private final ReportExportService service = new ReportExportService(new ObjectMapper());

    @Test
    void csvFlattensTheCompleteReportInsteadOfJavaObjectToString() {
        Map<String, Object> report = Map.of(
                "reportType", "FARM_SUMMARY",
                "dashboard", Map.of("totalBirds", 5000, "profitMinor", 300000),
                "sales", List.of(Map.of("saleDate", "2026-10-03", "totalAmountMinor", 125000))
        );

        String csv = service.csv(report);

        assertThat(csv).startsWith("\uFEFFpath,value\n");
        assertThat(csv).contains("\"dashboard.totalBirds\",\"5000\"");
        assertThat(csv).contains("\"dashboard.profitMinor\",\"300000\"");
        assertThat(csv).contains("\"sales[0].saleDate\",\"2026-10-03\"");
        assertThat(csv).contains("\"sales[0].totalAmountMinor\",\"125000\"");
        assertThat(csv).doesNotContain("java.util.");
    }

    @Test
    void pdfProducesARealPdfDocument() {
        byte[] pdf = service.pdf(
                Map.of("reportType", "BATCH_SUMMARY", "profitMinor", 500000),
                "Batch Report"
        );

        assertThat(pdf).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        assertThat(pdf).contains("%EOF".getBytes(StandardCharsets.US_ASCII));
    }
}
