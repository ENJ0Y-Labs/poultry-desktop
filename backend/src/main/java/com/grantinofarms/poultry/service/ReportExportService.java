package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class ReportExportService {
    private static final float MARGIN = 40f;
    private static final float LINE_HEIGHT = 14f;
    private static final float FONT_SIZE = 9f;
    private static final int PDF_LINE_WIDTH = 115;

    private final ObjectMapper objectMapper;

    public ReportExportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String csv(Map<String, Object> report) {
        JsonNode root = objectMapper.valueToTree(report);
        StringBuilder csv = new StringBuilder("\uFEFFpath,value\n");
        flatten(root, "", (path, value) -> csv
                .append(escapeCsv(path))
                .append(',')
                .append(escapeCsv(value))
                .append('\n'));
        return csv.toString();
    }

    public byte[] pdf(Map<String, Object> report, String title) {
        JsonNode root = objectMapper.valueToTree(report);

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(document);
            writer.writeTitle(title);
            flatten(root, "", writer::writeRow);
            writer.closePage();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate report PDF.", e);
        }
    }

    private void flatten(JsonNode node, String path, RowWriter writer) {
        if (node == null || node.isNull()) {
            writer.write(path, "");
            return;
        }

        if (node.isValueNode()) {
            writer.write(path, node.asText());
            return;
        }

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String childPath = path.isEmpty() ? field.getKey() : path + "." + field.getKey();
                flatten(field.getValue(), childPath, writer);
            }
            return;
        }

        if (node.isArray()) {
            if (node.isEmpty()) {
                writer.write(path, "");
                return;
            }
            for (int i = 0; i < node.size(); i++) {
                flatten(node.get(i), path + "[" + i + "]", writer);
            }
        }
    }

    private String escapeCsv(String value) {
        String safe = value == null ? "" : value;
        return """ + safe.replace(""", """") + """;
    }

    @FunctionalInterface
    private interface RowWriter {
        void write(String path, String value);
    }

    private static final class PdfWriter {
        private final PDDocument document;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;

        private PdfWriter(PDDocument document) throws IOException {
            this.document = document;
            openPage();
        }

        private void openPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void writeTitle(String title) {
            writeText(title, bold, 14f);
            writeText("Exported report values", regular, 9f);
            y -= 6f;
        }

        private void writeRow(String path, String value) {
            String text = path.isEmpty() ? value : path + ": " + value;
            for (String line : wrap(text, PDF_LINE_WIDTH)) {
                writeText(line, regular, FONT_SIZE);
            }
        }

        private void writeText(String text, PDType1Font font, float size) {
            ensureSpace();
            try {
                stream.beginText();
                stream.setFont(font, size);
                stream.newLineAtOffset(MARGIN, y);
                stream.showText(sanitize(text));
                stream.endText();
                y -= LINE_HEIGHT;
            } catch (IOException e) {
                throw new IllegalStateException("Could not write report PDF.", e);
            }
        }

        private void ensureSpace() {
            if (y > MARGIN) {
                return;
            }
            try {
                closePage();
                openPage();
            } catch (IOException e) {
                throw new IllegalStateException("Could not create report PDF page.", e);
            }
        }

        private void closePage() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private List<String> wrap(String text, int maxChars) {
            String safe = sanitize(text);
            if (safe.length() <= maxChars) {
                return List.of(safe);
            }

            List<String> lines = new ArrayList<>();
            String remaining = safe;
            while (remaining.length() > maxChars) {
                int split = remaining.lastIndexOf(' ', maxChars);
                if (split <= 0) {
                    split = maxChars;
                }
                lines.add(remaining.substring(0, split));
                remaining = remaining.substring(split).stripLeading();
            }
            lines.add(remaining);
            return lines;
        }

        private String sanitize(String value) {
            if (value == null) {
                return "";
            }
            StringBuilder safe = new StringBuilder(value.length());
            for (char c : value.toCharArray()) {
                safe.append(c >= 32 && c <= 255 ? c : '?');
            }
            return safe.toString();
        }
    }
}
