package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ProductionHistoryCsvImportService {
    private static final Set<String> MODULES = Set.of("egg-collections", "broiler-weights");
    private final BatchService batches;
    private final EggManagementService eggs;
    private final BroilerProductionService broilers;

    public ProductionHistoryCsvImportService(BatchService batches, EggManagementService eggs,
                                             BroilerProductionService broilers) {
        this.batches = batches;
        this.eggs = eggs;
        this.broilers = broilers;
    }

    public Map<String, Object> preview(CsvImportRequest request) {
        Validation v = validate(request);
        return Map.of("module", v.module, "valid", v.errors == 0, "imported", 0,
                "duplicates", v.duplicates, "rejected", v.errors, "rows", v.results);
    }

    @Transactional
    public Map<String, Object> importRecords(CsvImportRequest request) {
        Validation v = validate(request);
        if (v.errors > 0) {
            Row first = v.rows.stream().filter(r -> !r.errors.isEmpty()).findFirst().orElseThrow();
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_IMPORT_ROW_INVALID",
                    "CSV row " + first.rowNumber + ": " + String.join(" ", first.errors)
                            + " No rows were imported.");
        }
        List<Row> accepted = v.rows.stream().filter(r -> !r.duplicate)
                .sorted(Comparator.comparing((Row r) -> r.date).thenComparingInt(r -> r.rowNumber)).toList();
        int imported = 0;
        for (Row row : accepted) {
            if ("egg-collections".equals(v.module)) {
                eggs.addCollection(row.batch.id(), new EggCollectionRequest(
                        row.date, row.good, row.cracked, row.notes));
            } else {
                broilers.addWeight(row.batch.id(), new WeightRecordRequest(
                        row.date, row.sampleQuantity, row.totalWeightKg, row.notes));
            }
            imported++;
        }
        return Map.of("module", v.module, "imported", imported, "duplicates", v.duplicates, "rejected", 0);
    }

    private Validation validate(CsvImportRequest request) {
        String module = request.module() == null ? "" : request.module().trim().toLowerCase(Locale.ROOT);
        if (!MODULES.contains(module)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_MODULE_UNSUPPORTED",
                    "Supported production imports are egg-collections and broiler-weights.");
        }
        List<String> headers = "egg-collections".equals(module)
                ? List.of("batch_code", "record_date", "good_eggs", "cracked_eggs", "notes")
                : List.of("batch_code", "record_date", "sample_quantity", "total_weight_kg", "notes");
        Map<String, BatchResponse> batchByCode = new HashMap<>();
        for (BatchResponse batch : batches.list()) batchByCode.put(batch.code().toUpperCase(Locale.ROOT), batch);
        List<Map<String, Object>> results = new ArrayList<>();
        List<Row> parsed = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int errors = 0, duplicates = 0;
        List<Map<String, String>> records = request.records() == null ? List.of() : request.records();
        if (records.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_IMPORT_EMPTY", "The import contains no data rows.");

        for (int i = 0; i < records.size(); i++) {
            Map<String, String> values = records.get(i);
            int rowNumber = i + 2;
            Row row = new Row(rowNumber);
            for (String key : values.keySet()) if (!headers.contains(key)) row.errors.add("Unexpected column '" + key + "'.");
            for (String header : headers) if (!values.containsKey(header)) row.errors.add("Missing column '" + header + "'.");
            String code = clean(values.get("batch_code"));
            if (code == null) row.errors.add("batch_code is required.");
            else {
                row.batch = batchByCode.get(code.toUpperCase(Locale.ROOT));
                if (row.batch == null) row.errors.add("No batch matches batch_code '" + code + "'.");
                else {
                    if (!"ACTIVE".equals(row.batch.status())) row.errors.add("The referenced batch is not ACTIVE.");
                    String requiredType = "egg-collections".equals(module) ? "LAYER" : "BROILER";
                    if (!requiredType.equals(row.batch.type())) row.errors.add("batch_code must reference a " + requiredType + " batch for this import.");
                }
                if (code.length() > 100) row.errors.add("batch_code exceeds the 100 character limit.");
            }
            try {
                String raw = clean(values.get("record_date"));
                if (raw == null) row.errors.add("record_date is required in YYYY-MM-DD format.");
                else row.date = LocalDate.parse(raw);
            } catch (DateTimeParseException ex) {
                row.errors.add("record_date must be a real date in YYYY-MM-DD format.");
            }
            if (row.date != null && row.batch != null && row.date.isBefore(row.batch.placementDate()))
                row.errors.add("record_date cannot be before batch placement date " + row.batch.placementDate() + ".");
            row.notes = clean(values.get("notes"));
            if (row.notes != null && row.notes.length() > 2000) row.errors.add("notes cannot exceed 2000 characters.");

            if ("egg-collections".equals(module)) {
                row.good = parseNonNegativeInt(values.get("good_eggs"), "good_eggs", row.errors);
                row.cracked = parseNonNegativeInt(values.get("cracked_eggs"), "cracked_eggs", row.errors);
            } else {
                row.sampleQuantity = parsePositiveInt(values.get("sample_quantity"), "sample_quantity", row.errors);
                try {
                    String raw = clean(values.get("total_weight_kg"));
                    if (raw == null) throw new NumberFormatException();
                    row.totalWeightKg = new BigDecimal(raw).setScale(3, RoundingMode.UNNECESSARY);
                    if (row.totalWeightKg.signum() <= 0) throw new NumberFormatException();
                } catch (NumberFormatException | ArithmeticException ex) {
                    row.errors.add("total_weight_kg must be positive and have at most 3 decimal places.");
                }
            }
            for (Map.Entry<String,String> entry : values.entrySet()) {
                if (entry.getValue() != null && entry.getValue().length() > 10000)
                    row.errors.add("Column '" + entry.getKey() + "' exceeds the 10000 character limit.");
            }

            if (row.errors.isEmpty()) {
                String key = duplicateKey(module, row);
                if (!seen.add(key)) row.duplicate = true;
                else if ("egg-collections".equals(module)) {
                    row.duplicate = eggs.collections(row.batch.id(), row.date).stream().anyMatch(existing ->
                            existing.recordDate().equals(row.date) && existing.good() == row.good
                                    && existing.cracked() == row.cracked
                                    && Objects.equals(clean(existing.notes()), row.notes));
                } else {
                    row.duplicate = broilers.weights(row.batch.id(), row.date).stream().anyMatch(existing ->
                            existing.recordDate().equals(row.date)
                                    && existing.sampleQuantity() == row.sampleQuantity
                                    && existing.totalWeightKg().compareTo(row.totalWeightKg) == 0
                                    && Objects.equals(clean(existing.notes()), row.notes));
                }
            }
            if (row.duplicate) duplicates++;
            if (!row.errors.isEmpty()) errors++;
            Map<String, Object> rowResult = new LinkedHashMap<>();
            rowResult.put("rowNumber", rowNumber);
            rowResult.put("valid", row.errors.isEmpty());
            rowResult.put("duplicate", row.duplicate);
            rowResult.put("errors", List.copyOf(row.errors));
            results.add(rowResult);
            parsed.add(row);
        }
        if ("egg-collections".equals(module)) {
            Map<String, List<Row>> byBatch = new HashMap<>();
            parsed.stream().filter(row -> row.errors.isEmpty() && !row.duplicate)
                    .sorted(Comparator.comparing((Row row) -> row.date).thenComparingInt(row -> row.rowNumber))
                    .forEach(row -> byBatch.computeIfAbsent(row.batch.id(), ignored -> new ArrayList<>()).add(row));
            for (Map.Entry<String, List<Row>> entry : byBatch.entrySet()) {
                List<Row> accepted = new ArrayList<>();
                for (Row row : entry.getValue()) {
                    List<EggCollectionRequest> proposed = new ArrayList<>();
                    for (Row prior : accepted) {
                        proposed.add(new EggCollectionRequest(prior.date, prior.good, prior.cracked, prior.notes));
                    }
                    proposed.add(new EggCollectionRequest(row.date, row.good, row.cracked, row.notes));
                    try {
                        eggs.validateHistoricalCollections(entry.getKey(), proposed);
                        accepted.add(row);
                    } catch (ApiException ex) {
                        row.errors.add("good_eggs: " + ex.getMessage());
                        errors++;
                        Map<String, Object> result = results.get(row.rowNumber - 2);
                        result.put("valid", false);
                        result.put("errors", List.copyOf(row.errors));
                    }
                }
            }
        }
        return new Validation(module, parsed, results, errors, duplicates);
    }

    private String duplicateKey(String module, Row r) {
        return String.join("|", module, r.batch.id(), r.date.toString(),
                String.valueOf(r.good), String.valueOf(r.cracked), String.valueOf(r.sampleQuantity),
                r.totalWeightKg == null ? "" : r.totalWeightKg.stripTrailingZeros().toPlainString(),
                Objects.toString(r.notes, ""));
    }
    private Integer parseNonNegativeInt(String value, String field, List<String> errors) {
        try { String raw = clean(value); if (raw == null) throw new NumberFormatException();
            int n = Integer.parseInt(raw); if (n < 0) throw new NumberFormatException(); return n;
        } catch (NumberFormatException ex) { errors.add(field + " must be a non-negative whole number."); return null; }
    }
    private Integer parsePositiveInt(String value, String field, List<String> errors) {
        try { String raw = clean(value); if (raw == null) throw new NumberFormatException();
            int n = Integer.parseInt(raw); if (n <= 0) throw new NumberFormatException(); return n;
        } catch (NumberFormatException ex) { errors.add(field + " must be a positive whole number."); return null; }
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static final class Row {
        final int rowNumber; final List<String> errors = new ArrayList<>();
        BatchResponse batch; LocalDate date; String notes; Integer good, cracked, sampleQuantity;
        BigDecimal totalWeightKg; boolean duplicate;
        Row(int rowNumber) { this.rowNumber = rowNumber; }
    }
    private record Validation(String module, List<Row> rows, List<Map<String,Object>> results, int errors, int duplicates) {}
}
