package com.grantinofarms.poultry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.service.ReportExportService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExportServiceTest {
    private final ReportExportService service = new ReportExportService(new ObjectMapper());

    @Test
    void csvUsesStableStructuredHeadersAndIncludesDateScope() {
        Map<String, Object> report = Map.of(
                "reportType", "FARM_SUMMARY",
                "asOf", "2026-10-03",
                "dashboard", Map.of("totalBirds", 5000, "profitMinor", 300000),
                "sales", List.of(Map.of("saleDate", "2026-10-03", "totalAmountMinor", 125000))
        );

        String csv = service.csv(report);

        assertThat(csv).startsWith("\uFEFF\"report_type\",\"as_of\",\"section\",\"record\",\"field\",\"value\",\"unit\"");
        assertThat(csv).contains("\"FARM_SUMMARY\",\"2026-10-03\",\"Dashboard\",\"—\",\"Total birds\",\"5000\",\"count\"");
        assertThat(csv).contains("₦3,000.00");
        assertThat(csv).contains("\"Sales\",\"2026-10-03\",\"Sale date\",\"2026-10-03\"");
    }

    @Test
    void csvHandlesEmptySectionsAndEscapesFormulaLikeValues() {
        String csv = service.csv(Map.of("reportType", "BATCH_SUMMARY", "asOf", "2026-01-01", "sales", List.of(), "customer", "=SUM(A1:A2)"));

        assertThat(csv).contains("\"No records\"");
        assertThat(csv).contains("'=SUM(A1:A2)");
        assertThat(csv).contains("\"field\",\"value\",\"unit\"");
    }

    @Test
    void pdfHasReportTitleDateStructuredHeadingsAndFormattedMoney() throws Exception {
        byte[] pdf = service.pdf(
                Map.of("reportType", "BATCH_SUMMARY", "asOf", "2026-10-03", "profitMinor", 500000, "farmName", "Grantino Farms"),
                "Batch Report"
        );

        assertThat(pdf).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        assertThat(pdf).contains("%EOF".getBytes(StandardCharsets.US_ASCII));
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Batch Report", "As of: 2026-10-03", "Section", "Record", "Field", "Value", "Unit", "₦5,000.00");
        }
    }
}
