package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class HealthHistoryCsvImportService {
    private static final Set<String> MODULES = Set.of("health", "drugs", "vaccinations");
    private final BatchService batches;
    private final HealthManagementService health;

    public HealthHistoryCsvImportService(BatchService batches, HealthManagementService health) {
        this.batches = batches;
        this.health = health;
    }

    public Map<String, Object> preview(CsvImportRequest request) {
        Validation v = validate(request);
        return Map.of("module", v.module, "valid", v.errors == 0, "imported", 0,
                "duplicates", v.duplicates, "rejected", v.errors, "rows", v.rowResults);
    }

    @Transactional
    public Map<String, Object> importRecords(CsvImportRequest request) {
        Validation v = validate(request);
        if (v.errors > 0) {
            Row first = v.rows.stream().filter(row -> !row.errors.isEmpty()).findFirst().orElseThrow();
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_IMPORT_ROW_INVALID",
                    "CSV row " + first.rowNumber + ": " + String.join(" ", first.errors) + " No rows were imported.");
        }
        int imported = 0;
        for (Row row : v.rows) {
            if (row.duplicate) continue;
            switch (v.module) {
                case "health" -> health.addHealth(row.batch.id(),
                        new HealthRecordRequest(row.date, row.condition, row.description, row.action));
                case "drugs" -> health.addDrug(row.batch.id(),
                        new DrugRecordRequest(row.date, row.drug, row.quantity, row.costMinor, row.reason));
                case "vaccinations" -> health.addVaccination(row.batch.id(),
                        new VaccinationRecordRequest(row.date, row.vaccine, row.dose, row.quantity, row.notes));
                default -> throw new IllegalStateException("Validated module became unsupported.");
            }
            imported++;
        }
        return Map.of("module", v.module, "imported", imported, "duplicates", v.duplicates, "rejected", 0);
    }

    private Validation validate(CsvImportRequest request) {
        String module = request.module() == null ? "" : request.module().trim().toLowerCase(Locale.ROOT);
        if (!MODULES.contains(module)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_MODULE_UNSUPPORTED",
                    "Supported health imports are health, drugs, and vaccinations.");
        }
        List<String> headers = switch (module) {
            case "health" -> List.of("batch_code", "record_date", "condition_problem", "description", "action");
            case "drugs" -> List.of("batch_code", "record_date", "drug", "quantity", "cost_minor", "reason");
            default -> List.of("batch_code", "record_date", "vaccine", "dose", "quantity", "notes");
        };
        Map<String, BatchResponse> batchByCode = new HashMap<>();
        for (BatchResponse batch : batches.list()) batchByCode.put(batch.code().toUpperCase(Locale.ROOT), batch);

        List<Row> parsed = new ArrayList<>();
        List<Map<String, Object>> rowResults = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, List<HealthRecordResponse>> existingHealth = new HashMap<>();
        Map<String, List<DrugRecordResponse>> existingDrugs = new HashMap<>();
        Map<String, List<VaccinationRecordResponse>> existingVaccinations = new HashMap<>();
        int errors = 0, duplicates = 0;
        List<Map<String, String>> records = request.records() == null ? List.of() : request.records();
        if (records.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_IMPORT_EMPTY", "The import contains no data rows.");

        for (int i = 0; i < records.size(); i++) {
            Map<String, String> values = records.get(i);
            int rowNumber = i + 2;
            List<String> rowErrors = new ArrayList<>();
            for (String key : values.keySet()) if (!headers.contains(key)) rowErrors.add("Unexpected column '" + key + "'.");
            for (String header : headers) if (!values.containsKey(header)) rowErrors.add("Missing column '" + header + "'.");

            String code = clean(values.get("batch_code"));
            BatchResponse batch = code == null ? null : batchByCode.get(code.toUpperCase(Locale.ROOT));
            if (code == null) rowErrors.add("batch_code is required.");
            else if (batch == null) rowErrors.add("No batch matches batch_code '" + code + "' on this farm.");
            else if (!"ACTIVE".equals(batch.status())) rowErrors.add("batch_code must reference an ACTIVE batch.");
            if (code != null && code.length() > 100) rowErrors.add("batch_code exceeds the 100 character limit.");
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getValue() != null && entry.getValue().length() > 10000)
                    rowErrors.add("Column '" + entry.getKey() + "' exceeds the 10000 character limit.");
            }

            LocalDate date = null;
            try {
                String rawDate = clean(values.get("record_date"));
                if (rawDate == null) rowErrors.add("record_date is required in YYYY-MM-DD format.");
                else date = LocalDate.parse(rawDate);
            } catch (DateTimeParseException ex) {
                rowErrors.add("record_date must be a real date in YYYY-MM-DD format.");
            }
            if (date != null && batch != null && date.isBefore(batch.placementDate()))
                rowErrors.add("record_date cannot be before batch placement date " + batch.placementDate() + ".");

            LocalDate rowDate = date;
            Row row = new Row(rowNumber, module, batch, date, values);
            if (module.equals("health")) {
                row.condition = required(values, "condition_problem", rowErrors);
                row.description = required(values, "description", rowErrors);
                row.action = required(values, "action", rowErrors);
            } else if (module.equals("drugs")) {
                row.drug = required(values, "drug", rowErrors);
                row.reason = required(values, "reason", rowErrors);
                row.quantity = parsePositiveInt(values.get("quantity"), "quantity", rowErrors);
                row.costMinor = parsePositiveLong(values.get("cost_minor"), "cost_minor", rowErrors);
            } else {
                row.vaccine = required(values, "vaccine", rowErrors);
                row.dose = required(values, "dose", rowErrors);
                row.notes = clean(values.get("notes"));
                row.quantity = parsePositiveInt(values.get("quantity"), "quantity", rowErrors);
            }

            if (batch != null && date != null && rowErrors.isEmpty()) {
                String existingKey;
                if (module.equals("health")) {
                    List<HealthRecordResponse> list = existingHealth.computeIfAbsent(batch.id(), health::health);
                    existingKey = healthKey(batch.id(), date, row.condition, row.description, row.action);
                    row.duplicate = !seen.add(existingKey) || list.stream().anyMatch(r ->
                            r.recordDate().equals(rowDate) && eq(r.conditionProblem(), row.condition)
                                    && eq(r.description(), row.description) && eq(r.action(), row.action));
                } else if (module.equals("drugs")) {
                    List<DrugRecordResponse> list = existingDrugs.computeIfAbsent(batch.id(), health::drugs);
                    existingKey = drugKey(batch.id(), date, row.drug, row.quantity, row.costMinor, row.reason);
                    row.duplicate = !seen.add(existingKey) || list.stream().anyMatch(r ->
                            r.recordDate().equals(date) && eq(r.drug(), row.drug) && r.quantity() == row.quantity
                                    && r.costMinor() == row.costMinor && eq(r.reason(), row.reason));
                } else {
                    List<VaccinationRecordResponse> list = existingVaccinations.computeIfAbsent(batch.id(), health::vaccinations);
                    existingKey = vaccineKey(batch.id(), date, row.vaccine, row.dose, row.quantity, row.notes);
                    row.duplicate = !seen.add(existingKey) || list.stream().anyMatch(r ->
                            r.recordDate().equals(date) && eq(r.vaccine(), row.vaccine) && eq(r.dose(), row.dose)
                                    && r.quantity() == row.quantity && eqNullable(r.notes(), row.notes));
                }
            }
            if (row.duplicate) duplicates++;
            row.errors.addAll(rowErrors);
            if (!row.errors.isEmpty()) errors++;
            rowResults.add(Map.of("rowNumber", rowNumber, "valid", row.errors.isEmpty(),
                    "duplicate", row.duplicate, "errors", List.copyOf(row.errors)));
            parsed.add(row);
        }
        return new Validation(module, parsed, rowResults, errors, duplicates);
    }

    private String required(Map<String, String> values, String field, List<String> errors) {
        String value = clean(values.get(field));
        if (value == null) errors.add(field + " is required.");
        return value;
    }
    private Integer parsePositiveInt(String value, String field, List<String> errors) {
        try {
            String raw = clean(value);
            if (raw == null) throw new NumberFormatException();
            int number = Integer.parseInt(raw);
            if (number <= 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException ex) {
            errors.add(field + " must be a positive whole number.");
            return null;
        }
    }
    private Long parsePositiveLong(String value, String field, List<String> errors) {
        try {
            String raw = clean(value);
            if (raw == null) throw new NumberFormatException();
            long number = Long.parseLong(raw);
            if (number <= 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException ex) {
            errors.add(field + " must be a positive whole number of minor currency units.");
            return null;
        }
    }
    private String healthKey(String batch, LocalDate date, String condition, String description, String action) {
        return String.join("|", "health", batch, date.toString(), norm(condition), norm(description), norm(action));
    }
    private String drugKey(String batch, LocalDate date, String drug, Integer quantity, Long cost, String reason) {
        return String.join("|", "drugs", batch, date.toString(), norm(drug), String.valueOf(quantity), String.valueOf(cost), norm(reason));
    }
    private String vaccineKey(String batch, LocalDate date, String vaccine, String dose, Integer quantity, String notes) {
        return String.join("|", "vaccinations", batch, date.toString(), norm(vaccine), norm(dose), String.valueOf(quantity), norm(notes));
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String norm(String value) { return clean(value) == null ? "" : clean(value); }
    private boolean eq(String left, String right) { return Objects.equals(norm(left), norm(right)); }
    private boolean eqNullable(String left, String right) { return Objects.equals(clean(left), clean(right)); }

    private static final class Row {
        final int rowNumber; final String module; final BatchResponse batch; final LocalDate date;
        final List<String> errors = new ArrayList<>();
        boolean duplicate;
        String condition, description, action, drug, reason, vaccine, dose, notes;
        Integer quantity; Long costMinor;
        Row(int rowNumber, String module, BatchResponse batch, LocalDate date, Map<String, String> values) {
            this.rowNumber = rowNumber; this.module = module; this.batch = batch; this.date = date;
        }
    }
    private record Validation(String module, List<Row> rows, List<Map<String, Object>> rowResults, int errors, int duplicates) {}
}
