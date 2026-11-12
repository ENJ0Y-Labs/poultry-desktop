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
                "reportTitle", "Farm Report",
                "farmName", "Grantino Farms",
                "generatedAt", "2026-10-03T12:00:00+01:00",
                "currency", "NGN",
                "dateFormat", "DD/MM/YYYY",
                "asOf", "2026-10-03",
                "waterUnitLabel", "gallons",
                "dashboard", Map.of("totalBirds", 5000, "profitMinor", 300000),
                "sales", List.of(Map.of("saleDate", "2026-10-03", "totalAmountMinor", 125000))
        );

        String csv = service.csv(report);

        assertThat(csv).startsWith("\uFEFF\"farm_name\",\"report_title\",\"generated_at\",\"selected_period\",\"currency\",\"as_of\",\"report_type\",\"section\",\"record\",\"field\",\"value\",\"unit\"");
        assertThat(csv).contains("\"Grantino Farms\",\"Farm Report\",\"2026-10-03T12:00:00+01:00\",\"As of 03/10/2026\",\"NGN\",\"03/10/2026\",\"FARM_SUMMARY\",\"Dashboard\",\"—\",\"Total birds\",\"5000\",\"count\"");
        assertThat(csv).contains("₦3,000.00");
        assertThat(csv).contains("\"Sales\",\"03/10/2026\",\"Sale date\",\"03/10/2026\"");
        assertThat(csv).contains("\"Water unit label\",\"gallons\"");
    }

    @Test
    void csvHandlesEmptySectionsAndEscapesFormulaLikeValues() {
        String csv = service.csv(Map.of("reportType", "BATCH_SUMMARY", "asOf", "2026-01-01", "sales", List.of(), "customer", "=SUM(A1:A2)"));

        assertThat(csv).contains("\"No records\"");
        assertThat(csv).contains("'=SUM(A1:A2)");
        assertThat(csv).contains("\"field\",\"value\",\"unit\"");
    }

    @Test
    void pdfRepeatsReportContextAndTableHeadersAcrossPages() throws Exception {
        List<Map<String, Object>> sales = java.util.stream.IntStream.range(0, 160)
                .mapToObj(index -> Map.<String, Object>of(
                        "saleDate", "2026-10-03",
                        "saleReference", "SALE-" + index,
                        "totalAmountMinor", 125000
                ))
                .toList();
        byte[] pdf = service.pdf(Map.of(
                "reportType", "FARM_SUMMARY",
                "farmName", "Grantino Farms",
                "generatedAt", "2026-10-03T12:00:00+01:00",
                "dateFormat", "DD/MM/YYYY",
                "currency", "NGN",
                "asOf", "2026-10-03",
                "sales", sales
        ), "Farm Report");

        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text.split("Section", -1).length - 1).isGreaterThan(1);
            assertThat(text).contains("Grantino Farms", "Farm Report (continued)", "Currency: NGN");
        }
    }

    @Test
    void pdfHasReportTitleDateStructuredHeadingsAndFormattedMoney() throws Exception {
        byte[] pdf = service.pdf(
                Map.of("reportType", "BATCH_SUMMARY", "asOf", "2026-10-03", "dateFormat", "DD/MM/YYYY", "currency", "NGN", "generatedAt", "2026-10-03T12:00:00+01:00", "reportTitle", "Batch Report", "waterUnitLabel", "litres", "profitMinor", 500000, "farmName", "Grantino Farms"),
                "Batch Report"
        );

        assertThat(pdf).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        assertThat(pdf).contains("%EOF".getBytes(StandardCharsets.US_ASCII));
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Grantino Farms", "Batch Report", "Generated: 03/10/2026 12:00 +01:00", "Selected period: As of 03/10/2026", "Currency: NGN", "Section", "Record", "Field", "Value", "Unit", "₦5,000.00", "Water unit label", "litres");
        }
    }
}
