package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportExportService {
    private static final float MARGIN = 36f;
    private static final float LINE_HEIGHT = 13f;
    private final ObjectMapper objectMapper;

    public ReportExportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String csv(Map<String, Object> report) {
        List<ExportRow> rows = rows(report);
        String reportType = text(report.get("reportType"));
        String farmName = text(report.get("farmName"));
        String reportTitle = text(report.get("reportTitle"));
        String batchCode = text(report.get("batchCode"));
        String batchType = text(report.get("batchType"));
        String houseId = text(report.get("houseId"));
        String currency = currencyCode(report);
        String dateFormat = dateFormat(report);
        String generatedAt = formatGeneratedAt(text(report.get("generatedAt")), dateFormat);
        String asOf = formatDate(text(report.get("asOf")), dateFormat);
        String selectedPeriod = asOf.isBlank() ? "Not specified" : "As of " + asOf;
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendCsv(csv, List.of("farm_name", "report_title", "batch_code", "batch_type", "house_id", "generated_at", "selected_period", "currency", "as_of", "report_type", "section", "record", "field", "value", "unit"));
        for (ExportRow row : rows) {
            appendCsv(csv, List.of(farmName, reportTitle, batchCode, batchType, houseId, generatedAt, selectedPeriod, currency, asOf, reportType, row.section(), row.record(), row.field(), row.value(), row.unit()));
        }
        return csv.toString();
    }

    public byte[] pdf(Map<String, Object> report, String title) {
        List<ExportRow> rows = rows(report);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfTable writer = new PdfTable(document);
            writer.title(text(report.get("farmName")), title, text(report.get("batchCode")), text(report.get("batchType")),
                    text(report.get("houseId")), formatGeneratedAt(text(report.get("generatedAt")), dateFormat(report)),
                    text(report.get("asOf")), currencyCode(report), dateFormat(report));
            writer.header();
            for (ExportRow row : rows) {
                writer.row(List.of(row.section(), row.record(), row.field(), row.value(), row.unit()));
            }
            writer.closePage();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate report PDF.", e);
        }
    }

    private List<ExportRow> rows(Map<String, Object> report) {
        JsonNode root = objectMapper.valueToTree(report);
        String currency = currencyCode(report);
        String dateFormat = dateFormat(report);
        List<ExportRow> result = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String section = humanize(field.getKey());
            if (List.of("asOf", "reportType", "farmName", "farmLocation", "timezone", "currency", "dateFormat", "generatedAt", "reportTitle", "batchCode", "batchType", "houseId").contains(field.getKey())) continue;
            appendRows(field.getValue(), section, "", field.getKey(), result, currency, dateFormat);
        }
        return result;
    }

    private void appendRows(JsonNode node, String section, String record, String fieldPath, List<ExportRow> rows, String currency, String dateFormat) {
        if (node == null || node.isNull()) {
            rows.add(new ExportRow(section, displayRecord(record), humanize(fieldPath), "", unit(fieldPath, currency)));
            return;
        }
        if (node.isValueNode()) {
            rows.add(new ExportRow(section, displayRecord(record), humanize(fieldPath), formatValue(node, fieldPath, currency, dateFormat), unit(fieldPath, currency)));
            return;
        }
        if (node.isArray()) {
            if (node.isEmpty()) {
                rows.add(new ExportRow(section, "—", "Records", "No records", ""));
                return;
            }
            for (int i = 0; i < node.size(); i++) {
                JsonNode item = node.get(i);
                String itemRecord = recordName(item, i, dateFormat);
                appendRows(item, section, itemRecord, "", rows, currency, dateFormat);
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String childPath = field.getKey();
                appendRows(field.getValue(), section, record, childPath, rows, currency, dateFormat);
            }
        }
    }

    private String displayRecord(String record) {
        return record == null || record.isBlank() ? "—" : record;
    }

    private String recordName(JsonNode item, int index, String dateFormat) {
        if (item != null && item.isObject()) {
            for (String key : List.of("code", "batchCode", "name", "id", "recordDate", "saleDate", "occurredDate", "date")) {
                JsonNode value = item.get(key);
                if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                    return key.toLowerCase(Locale.ROOT).endsWith("date") ? formatDate(value.asText(), dateFormat) : value.asText();
                }
            }
        }
        return "Record " + (index + 1);
    }

    private String formatValue(JsonNode value, String key, String currencyCode, String dateFormat) {
        if (key.toLowerCase(Locale.ROOT).endsWith("minor")) {
            try {
                java.util.Currency currency = java.util.Currency.getInstance(currencyCode);
                NumberFormat amount = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-NG"));
                amount.setMinimumFractionDigits(2);
                amount.setMaximumFractionDigits(2);
                java.math.BigDecimal minorAmount = java.math.BigDecimal.valueOf(value.asLong(), 2);
                return (minorAmount.signum() < 0 ? "-" : "")
                        + currency.getSymbol(Locale.forLanguageTag("en-NG"))
                        + amount.format(minorAmount.abs());
            } catch (RuntimeException ignored) {
                return value.asText();
            }
        }
        if (value.isTextual() && key.toLowerCase(Locale.ROOT).matches(".*(date|asof)$")) {
            return formatDate(value.asText(), dateFormat);
        }
        if (key.toLowerCase(Locale.ROOT).contains("percent") && value.isNumber()) {
            return String.format(Locale.ROOT, "%.2f%%", value.asDouble());
        }
        return value.asText();
    }

    private String unit(String key, String currencyCode) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith("minor")) return currencyCode;
        if (lower.contains("percent")) return "%";
        if (lower.contains("kg") || lower.contains("weight")) return "kg";
        if (lower.contains("quantitymilli")) return "units";
        if (lower.contains("eggs") || lower.contains("birds") || lower.contains("mortality") || lower.contains("culling")) return "count";
        return "";
    }

    private String currencyCode(Map<String, Object> report) {
        String currency = text(report.get("currency"));
        return currency.isBlank() ? "NGN" : currency.toUpperCase(Locale.ROOT);
    }

    private String dateFormat(Map<String, Object> report) {
        String format = text(report.get("dateFormat"));
        return format.isBlank() ? "YYYY-MM-DD" : format;
    }

    private static String formatGeneratedAt(String value, String format) {
        if (value == null || value.isBlank()) return "";
        try {
            OffsetDateTime timestamp = OffsetDateTime.parse(value);
            return formatDate(timestamp.toLocalDate().toString(), format)
                    + " " + timestamp.toLocalTime().withNano(0) + " " + timestamp.getOffset();
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    private static String formatDate(String value, String format) {
        if (value == null || value.isBlank()) return value == null ? "" : value;
        try {
            LocalDate date = LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
            return switch (format) {
                case "DD/MM/YYYY" -> date.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"));
                case "DD-MM-YYYY" -> date.format(DateTimeFormatter.ofPattern("dd-MM-uuuu"));
                default -> date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            };
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    private String humanize(String key) {
        String words = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').replace('.', ' ').toLowerCase(Locale.ROOT);
        return words.isEmpty() ? words : words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private void appendCsv(StringBuilder csv, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) csv.append(',');
            String value = values.get(i) == null ? "" : values.get(i);
            boolean numeric = value.matches("-?\\d+(\\.\\d+)?");
            if (!numeric && value.matches("(?s)^[\\s\\p{Cc}]*[=+@-].*")) value = "'" + value;
            String escaped = value.replace(String.valueOf('"'), String.valueOf('"') + '"');
            csv.append('"').append(escaped).append('"');
        }
        csv.append('\n');
    }

    private record ExportRow(String section, String record, String field, String value, String unit) {}

    private static final class PdfTable {
        private final PDDocument document;
        private final PDFont regular;
        private final PDFont bold;
        private PDPageContentStream stream;
        private float y;
        private String farmName;
        private String reportTitle;
        private String batchCode;
        private String batchType;
        private String houseId;
        private String generatedAt;
        private String asOf;
        private String currency;
        private String dateFormat;
        private float[] widths;

        private PdfTable(PDDocument document) throws IOException {
            this.document = document;
            Path[] fonts = findFonts();
            if (fonts == null) throw new IOException("A supported Unicode font is required for PDF export.");
            regular = PDType0Font.load(document, fonts[0].toFile());
            bold = PDType0Font.load(document, fonts[1].toFile());
            openPage();
        }

        private Path[] findFonts() {
            List<Path> directories = new ArrayList<>();
            String windows = System.getenv("WINDIR");
            if (windows != null && !windows.isBlank()) directories.add(Path.of(windows, "Fonts"));
            String local = System.getenv("LOCALAPPDATA");
            if (local != null && !local.isBlank()) directories.add(Path.of(local, "Microsoft", "Windows", "Fonts"));
            directories.addAll(List.of(Path.of("/usr/share/fonts/truetype/dejavu"), Path.of("/usr/share/fonts/truetype/liberation2"), Path.of("/usr/share/fonts/truetype/liberation"), Path.of("/usr/share/fonts/truetype/freefont"), Path.of("/System/Library/Fonts/Supplemental"), Path.of("/Library/Fonts")));
            String[][] names = {{"arial.ttf","arialbd.ttf"},{"Arial.ttf","Arial Bold.ttf"},{"segoeui.ttf","segoeuib.ttf"},{"DejaVuSans.ttf","DejaVuSans-Bold.ttf"},{"LiberationSans-Regular.ttf","LiberationSans-Bold.ttf"},{"FreeSans.ttf","FreeSansBold.ttf"}};
            for (Path directory : directories) for (String[] pair : names) {
                Path regular = directory.resolve(pair[0]), bold = directory.resolve(pair[1]);
                if (Files.isRegularFile(regular) && Files.isRegularFile(bold)) return new Path[]{regular,bold};
            }
            return null;
        }

        private void openPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            widths = new float[]{95f, 90f, 150f, 125f, 45f};
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void title(String farmName, String reportTitle, String batchCode, String batchType, String houseId,
                           String generatedAt, String asOf, String currency, String dateFormat) {
            this.farmName = farmName;
            this.reportTitle = reportTitle;
            this.batchCode = batchCode;
            this.batchType = batchType;
            this.houseId = houseId;
            this.generatedAt = generatedAt;
            this.asOf = asOf;
            this.currency = currency;
            this.dateFormat = dateFormat;
            writeText(farmName.isBlank() ? "Farm report" : farmName, bold, 15f);
            writeText(reportTitle, bold, 12f);
            if (!batchCode.isBlank()) writeText("Batch: " + batchCode + (batchType.isBlank() ? "" : " (" + batchType + ")"), regular, 9f);
            if (!houseId.isBlank()) writeText("House ID: " + houseId, regular, 9f);
            writeText("Generated: " + (generatedAt.isBlank() ? "Not specified" : generatedAt), regular, 9f);
            writeText("Selected period: As of " + (asOf.isBlank() ? "Not specified" : formatDate(asOf, dateFormat)), regular, 9f);
            writeText("Currency: " + currency, regular, 9f);
            y -= 8;
        }

        private void continued() {
            writeText((farmName.isBlank() ? "Farm report" : farmName) + " · " + reportTitle + " (continued)", bold, 10f);
            if (!batchCode.isBlank()) writeText("Batch: " + batchCode + (batchType.isBlank() ? "" : " (" + batchType + ")"), regular, 8f);
            if (!houseId.isBlank()) writeText("House ID: " + houseId, regular, 8f);
            writeText("As of: " + (asOf.isBlank() ? "Not specified" : formatDate(asOf, dateFormat)) + " · Currency: " + currency, regular, 8f);
            y -= 5;
        }

        private void header() {
            drawRow(List.of("Section", "Record", "Field", "Value", "Unit"), true);
        }

        private void row(List<String> cells) {
            drawRow(cells, false);
        }

        private void drawRow(List<String> cells, boolean header) {
            List<List<String>> wrapped = new ArrayList<>();
            int max = 1;
            for (int i = 0; i < cells.size(); i++) {
                List<String> lines = wrap(sanitize(cells.get(i)), Math.max(8, (int)(widths[i] / 4.5f)));
                wrapped.add(lines);
                max = Math.max(max, lines.size());
            }
            if (y - max * LINE_HEIGHT < MARGIN) {
                try { closePage(); openPage(); continued(); header(); }
                catch (IOException e) { throw new IllegalStateException("Could not create report PDF page.", e); }
            }
            try {
                float rowHeight = max * LINE_HEIGHT + 5;
                stream.setLineWidth(0.4f);
                stream.moveTo(MARGIN, y + 4);
                stream.lineTo(PDRectangle.A4.getWidth() - MARGIN, y + 4);
                stream.stroke();
                for (int col = 0; col < wrapped.size(); col++) {
                    for (int line = 0; line < wrapped.get(col).size(); line++) {
                        float x = MARGIN;
                        for (int before = 0; before < col; before++) x += widths[before];
                        stream.beginText();
                        stream.setFont(header ? bold : regular, header ? 8.5f : 8f);
                        stream.newLineAtOffset(x, y - line * LINE_HEIGHT);
                        stream.showText(wrapped.get(col).get(line));
                        stream.endText();
                    }
                }
                y -= rowHeight;
            } catch (IOException e) {
                throw new IllegalStateException("Could not write report PDF table.", e);
            }
        }

        private List<String> wrap(String text, int maxChars) {
            if (text.length() <= maxChars) return List.of(text);
            List<String> lines = new ArrayList<>();
            String remaining = text;
            while (remaining.length() > maxChars) {
                int split = remaining.lastIndexOf(' ', maxChars);
                if (split <= 0) split = maxChars;
                lines.add(remaining.substring(0, split));
                remaining = remaining.substring(split).stripLeading();
            }
            lines.add(remaining);
            return lines;
        }

        private String sanitize(String text) {
            if (text == null) return "";
            StringBuilder result = new StringBuilder();
            for (char c : text.toCharArray()) result.append((c >= 32 && c != 127) || c == '\t' ? c : ' ');
            return result.toString();
        }

        private void writeText(String text, PDFont font, float size) {
            try {
                stream.beginText(); stream.setFont(font, size); stream.newLineAtOffset(MARGIN, y); stream.showText(sanitize(text)); stream.endText();
                y -= LINE_HEIGHT;
            } catch (IOException e) { throw new IllegalStateException("Could not write report PDF.", e); }
        }

        private void closePage() throws IOException {
            if (stream != null) { stream.close(); stream = null; }
        }
    }
}
