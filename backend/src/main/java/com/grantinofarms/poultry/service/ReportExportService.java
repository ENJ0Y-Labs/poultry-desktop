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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportExportService {
    private static final float MARGIN = 36f;
    private static final float LINE_HEIGHT = 13f;
    private static final String[] HEADERS = {"Section", "Record", "Field", "Value", "Unit"};
    private final ObjectMapper objectMapper;

    public ReportExportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String csv(Map<String, Object> report) {
        List<ExportRow> rows = rows(report);
        String reportType = text(report.get("reportType"));
        String asOf = text(report.get("asOf"));
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendCsv(csv, List.of("report_type", "as_of", "section", "record", "field", "value", "unit"));
        for (ExportRow row : rows) {
            appendCsv(csv, List.of(reportType, asOf, row.section(), row.record(), row.field(), row.value(), row.unit()));
        }
        return csv.toString();
    }

    public byte[] pdf(Map<String, Object> report, String title) {
        List<ExportRow> rows = rows(report);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfTable writer = new PdfTable(document);
            writer.title(title, text(report.get("asOf")));
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
        List<ExportRow> result = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String section = humanize(field.getKey());
            if (field.getKey().equals("asOf") || field.getKey().equals("reportType")) continue;
            appendRows(field.getValue(), section, "", field.getKey(), result);
        }
        return result;
    }

    private void appendRows(JsonNode node, String section, String record, String fieldPath, List<ExportRow> rows) {
        if (node == null || node.isNull()) {
            rows.add(new ExportRow(section, record, humanize(fieldPath), "", unit(fieldPath)));
            return;
        }
        if (node.isValueNode()) {
            rows.add(new ExportRow(section, record, humanize(fieldPath), formatValue(node, fieldPath), unit(fieldPath)));
            return;
        }
        if (node.isArray()) {
            if (node.isEmpty()) {
                rows.add(new ExportRow(section, "—", "Records", "No records", ""));
                return;
            }
            for (int i = 0; i < node.size(); i++) {
                JsonNode item = node.get(i);
                String itemRecord = recordName(item, i);
                appendRows(item, section, itemRecord, fieldPath, rows);
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String childPath = fieldPath.isEmpty() ? field.getKey() : fieldPath + "." + field.getKey();
                appendRows(field.getValue(), section, record, childPath, rows);
            }
        }
    }

    private String recordName(JsonNode item, int index) {
        if (item != null && item.isObject()) {
            for (String key : List.of("code", "batchCode", "name", "id", "recordDate", "saleDate", "occurredDate", "date")) {
                JsonNode value = item.get(key);
                if (value != null && value.isValueNode() && !value.asText().isBlank()) return value.asText();
            }
        }
        return "Record " + (index + 1);
    }

    private String formatValue(JsonNode value, String key) {
        if (key.toLowerCase(Locale.ROOT).endsWith("minor")) {
            try {
                long minor = value.asLong();
                NumberFormat currency = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-NG"));
                currency.setMinimumFractionDigits(2);
                currency.setMaximumFractionDigits(2);
                return "₦" + currency.format(minor / 100.0);
            } catch (RuntimeException ignored) {
                return value.asText();
            }
        }
        if (key.toLowerCase(Locale.ROOT).contains("percent") && value.isNumber()) {
            return String.format(Locale.ROOT, "%.2f%%", value.asDouble());
        }
        return value.asText();
    }

    private String unit(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith("minor")) return "NGN";
        if (lower.contains("percent")) return "%";
        if (lower.contains("kg") || lower.contains("weight")) return "kg";
        if (lower.contains("quantitymilli")) return "units";
        if (lower.contains("eggs") || lower.contains("birds") || lower.contains("mortality") || lower.contains("culling")) return "count";
        return "";
    }

    private String humanize(String key) {
        String words = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').replace('.', ' ');
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
            csv.append('"').append(value.replace(""", """")).append('"');
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

        private void title(String title, String asOf) {
            writeText(title, bold, 15f);
            writeText("As of: " + (asOf == null || asOf.isBlank() ? "Not specified" : asOf), regular, 10f);
            y -= 8;
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
                try { closePage(); openPage(); title("Report (continued)", ""); header(); }
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
