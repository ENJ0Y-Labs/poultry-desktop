package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.domain.BirdPopulationCalculator;
import com.grantinofarms.poultry.domain.BirdPopulationEvent;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.BirdPopulationRepository;
import com.grantinofarms.poultry.repository.DailyOperationsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class OperationalCsvImportService {
    private static final Set<String> MODULES = Set.of("daily-records", "mortality", "culling");

    private final BatchService batches;
    private final DailyOperationsService dailyOperations;
    private final BirdPopulationService population;
    private final BirdPopulationRepository populationRepository;
    private final DailyOperationsRepository dailyRepository;

    public OperationalCsvImportService(BatchService batches, DailyOperationsService dailyOperations,
                                       BirdPopulationService population, BirdPopulationRepository populationRepository,
                                       DailyOperationsRepository dailyRepository) {
        this.batches = batches;
        this.dailyOperations = dailyOperations;
        this.population = population;
        this.populationRepository = populationRepository;
        this.dailyRepository = dailyRepository;
    }

    public Map<String, Object> preview(CsvImportRequest request) {
        Validation validation = validate(request);
        return Map.of("module", validation.module, "valid", validation.errors == 0,
                "imported", 0, "duplicates", validation.duplicates, "rejected", validation.errors,
                "rows", validation.rows);
    }

    @Transactional
    public Map<String, Object> importRecords(CsvImportRequest request) {
        Validation validation = validate(request);
        if (validation.errors > 0) {
            Map<?, ?> first = (Map<?, ?>) validation.rows.stream()
                    .filter(row -> !((List<?>) row.get("errors")).isEmpty()).findFirst().orElseThrow();
            int rowNumber = (Integer) first.get("rowNumber");
            String message = String.join(" ", (List<String>) first.get("errors"));
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_IMPORT_ROW_INVALID",
                    "CSV row " + rowNumber + ": " + message + " No rows were imported.");
        }

        List<Row> accepted = validation.parsed.stream().filter(row -> !row.duplicate)
                .sorted(Comparator.comparing((Row row) -> row.date).thenComparingInt(row -> row.rowNumber)).toList();
        int imported = 0;
        for (Row row : accepted) {
            if (validation.module.equals("daily-records")) {
                List<WaterContainerEntryRequest> water = row.waterEntries.stream()
                        .map(entry -> new WaterContainerEntryRequest(entry.capacity, entry.count)).toList();
                dailyOperations.create(row.batch.id(), new DailyRecordRequest(row.date, row.notes, water));
            } else {
                BirdPopulationEventRequest event = new BirdPopulationEventRequest(row.date, row.quantity, row.reason, row.notes);
                if (validation.module.equals("mortality")) population.addMortality(row.batch.id(), event);
                else population.addCulling(row.batch.id(), event);
            }
            imported++;
        }
        return Map.of("module", validation.module, "imported", imported,
                "duplicates", validation.duplicates, "rejected", 0);
    }

    private Validation validate(CsvImportRequest request) {
        String module = request.module() == null ? "" : request.module().trim().toLowerCase(Locale.ROOT);
        if (!MODULES.contains(module)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_MODULE_UNSUPPORTED",
                    "Supported operational imports are daily-records, mortality, and culling.");
        }
        List<String> headers = module.equals("daily-records")
                ? List.of("batch_code", "record_date", "notes", "water_entries")
                : List.of("batch_code", "event_date", "quantity", "reason", "notes");
        Map<String, BatchResponse> batchByCode = new HashMap<>();
        for (BatchResponse batch : batches.list()) batchByCode.put(batch.code().toUpperCase(Locale.ROOT), batch);

        List<Map<String, Object>> rowResults = new ArrayList<>();
        List<Row> parsed = new ArrayList<>();
        Map<String, Row> seenKeys = new HashMap<>();
        int errors = 0, duplicates = 0;
        List<Map<String, String>> records = request.records() == null ? List.of() : request.records();
        if (records.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_IMPORT_EMPTY", "The import contains no data rows.");
        }

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
            else if (!"ACTIVE".equals(batch.status())) rowErrors.add("The referenced batch is not active and cannot accept operational records.");
            if (code != null && code.length() > 100) rowErrors.add("batch_code exceeds the 100 character limit.");
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getValue() != null && entry.getValue().length() > 10000) {
                    rowErrors.add("Column '" + entry.getKey() + "' exceeds the 10000 character limit.");
                }
            }

            LocalDate date = null;
            String dateField = module.equals("daily-records") ? "record_date" : "event_date";
            try {
                String rawDate = clean(values.get(dateField));
                if (rawDate == null) rowErrors.add(dateField + " is required in YYYY-MM-DD format.");
                else date = LocalDate.parse(rawDate);
            } catch (DateTimeParseException ex) {
                rowErrors.add(dateField + " must be a real date in YYYY-MM-DD format.");
            }
            if (date != null && batch != null && date.isBefore(batch.placementDate())) {
                rowErrors.add(dateField + " cannot be before the batch placement date " + batch.placementDate() + ".");
            }
            String notes = clean(values.get("notes"));
            String reason = clean(values.get("reason"));
            Integer quantity = null;
            List<WaterEntry> waterEntries = List.of();
            if (module.equals("daily-records")) {
                try {
                    waterEntries = parseWaterEntries(values.get("water_entries"));
                } catch (IllegalArgumentException ex) {
                    rowErrors.add("water_entries: " + ex.getMessage());
                }
            } else {
                try {
                    String raw = clean(values.get("quantity"));
                    if (raw == null) throw new IllegalArgumentException("quantity is required.");
                    quantity = Integer.valueOf(raw);
                    if (quantity <= 0) throw new IllegalArgumentException("quantity must be a positive whole number.");
                } catch (NumberFormatException ex) {
                    rowErrors.add("quantity must be a positive whole number.");
                } catch (IllegalArgumentException ex) {
                    rowErrors.add(ex.getMessage());
                }
            }

            if (module.equals("daily-records") && batch != null && rowErrors.isEmpty()) {
                String farmId = dailyRepository.farmIdForBatch(batch.id());
                for (WaterEntry entry : waterEntries) {
                    if (!dailyRepository.waterSizeBelongsToFarm(entry.capacity, farmId)) {
                        rowErrors.add("water_entries: capacity_units " + entry.capacity + " is not configured for this farm.");
                    }
                }
            }

            Row row = new Row(rowNumber, batch, date, quantity, reason, notes, waterEntries);
            if (rowErrors.isEmpty() && module.equals("daily-records")) {
                String key = batch.id() + "|" + date;
                Row previous = seenKeys.putIfAbsent(key, row);
                if (previous != null) {
                    if (sameDailyInput(previous, row)) row.duplicate = true;
                    else rowErrors.add("Duplicate batch_code/record_date within this file has conflicting details.");
                } else {
                    DailyRecordResponse existing = dailyOperations.get(batch.id(), date);
                    if (existing.id() != null) {
                        if (sameDaily(existing, row)) row.duplicate = true;
                        else rowErrors.add("A daily record already exists for this batch/date with different details.");
                    }
                }
            } else if (rowErrors.isEmpty()) {
                String type = module.equals("mortality") ? "MORTALITY" : "CULLING";
                String key = batch.id() + "|" + date + "|" + type + "|" + quantity + "|" + norm(reason) + "|" + norm(notes);
                Row previous = seenKeys.putIfAbsent(key, row);
                if (previous != null) row.duplicate = true;
                else {
                    DailyRecordResponse existing = dailyOperations.get(batch.id(), date);
                    int eventQuantity = quantity;
                    boolean exact = existing.populationEvents().stream().anyMatch(event ->
                            type.equals(event.eventType()) && event.quantity() == eventQuantity
                                    && Objects.equals(norm(event.reason()), norm(reason))
                                    && Objects.equals(norm(event.notes()), norm(notes)));
                    if (exact) row.duplicate = true;
                }
            }
            if (row.duplicate) duplicates++;
            if (!rowErrors.isEmpty()) errors++;
            Map<String, Object> rowResult = new HashMap<>();
            rowResult.put("rowNumber", rowNumber);
            rowResult.put("valid", rowErrors.isEmpty());
            rowResult.put("duplicate", row.duplicate);
            rowResult.put("errors", List.copyOf(rowErrors));
            rowResults.add(rowResult);
            row.errors.addAll(rowErrors);
            parsed.add(row);
        }

        if (!module.equals("daily-records")) {
            Map<String, List<BirdPopulationEvent>> eventsByBatch = new HashMap<>();
            Map<String, Integer> initialByBatch = new HashMap<>();
            parsed.stream().filter(row -> row.batch != null && row.date != null && row.quantity != null && row.errors.isEmpty() && !row.duplicate)
                    .sorted(Comparator.comparing((Row row) -> row.date).thenComparingInt(row -> row.rowNumber))
                    .forEach(row -> {
                        String batchId = row.batch.id();
                        List<BirdPopulationEvent> events = eventsByBatch.computeIfAbsent(batchId, populationRepository::findEvents);
                        initialByBatch.putIfAbsent(batchId, row.batch.initialBirdCount());
                        try {
                            BirdPopulationCalculator.validatePopulationChange(initialByBatch.get(batchId), events,
                                    new BirdPopulationEvent(row.date, module.equals("mortality") ? "MORTALITY" : "CULLING", row.quantity));
                            events.add(new BirdPopulationEvent(row.date, module.equals("mortality") ? "MORTALITY" : "CULLING", row.quantity));
                        } catch (IllegalArgumentException ex) {
                            row.errors.add("quantity: " + ex.getMessage() + " (population history is invalid on or after " + row.date + ").");
                        }
                    });
            errors = 0;
            for (int i = 0; i < parsed.size(); i++) {
                Row row = parsed.get(i);
                Map<String, Object> result = rowResults.get(i);
                result.put("errors", List.copyOf(row.errors));
                result.put("valid", row.errors.isEmpty());
                if (!row.errors.isEmpty()) errors++;
            }
        }

        return new Validation(module, parsed, rowResults, errors, duplicates);
    }

    private boolean sameDailyInput(Row left, Row right) {
        if (!Objects.equals(norm(left.notes), norm(right.notes))) return false;
        List<String> leftWater = left.waterEntries.stream().map(w -> w.capacity + ":" + w.count).sorted().toList();
        List<String> rightWater = right.waterEntries.stream().map(w -> w.capacity + ":" + w.count).sorted().toList();
        return leftWater.equals(rightWater);
    }

    private boolean sameDaily(DailyRecordResponse existing, Row row) {
        if (!Objects.equals(norm(existing.notes()), norm(row.notes))) return false;
        List<String> existingWater = existing.water().stream()
                .map(w -> w.capacityUnits() + ":" + w.containerCount()).sorted().toList();
        List<String> importedWater = row.waterEntries.stream()
                .map(w -> w.capacity + ":" + w.count).sorted().toList();
        return existingWater.equals(importedWater);
    }

    private List<WaterEntry> parseWaterEntries(String value) {
        String text = clean(value);
        if (text == null) return List.of();
        List<WaterEntry> entries = new ArrayList<>();
        Set<Integer> capacities = new HashSet<>();
        for (String item : text.split(";", -1)) {
            String[] parts = item.trim().split(":", -1);
            if (parts.length != 2) throw new IllegalArgumentException("use capacity_units:container_count pairs separated by semicolons, e.g. 10:2;20:1.");
            try {
                int capacity = Integer.parseInt(parts[0].trim());
                int count = Integer.parseInt(parts[1].trim());
                if (capacity <= 0 || count <= 0) throw new IllegalArgumentException("capacities and container counts must be positive whole numbers.");
                if (!capacities.add(capacity)) throw new IllegalArgumentException("each container capacity may appear only once per row.");
                entries.add(new WaterEntry(capacity, count));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("capacities and container counts must be positive whole numbers.");
            }
        }
        return List.copyOf(entries);
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String norm(String value) { return clean(value) == null ? "" : clean(value).toLowerCase(Locale.ROOT); }

    private static final class Row {
        final int rowNumber; final BatchResponse batch; final LocalDate date; final Integer quantity;
        final String reason; final String notes; final List<WaterEntry> waterEntries;
        final List<String> errors = new ArrayList<>(); boolean duplicate;
        Row(int rowNumber, BatchResponse batch, LocalDate date, Integer quantity, String reason, String notes, List<WaterEntry> waterEntries) {
            this.rowNumber=rowNumber; this.batch=batch; this.date=date; this.quantity=quantity;
            this.reason=reason; this.notes=notes; this.waterEntries=waterEntries;
        }
    }
    private record WaterEntry(int capacity, int count) {}
    private record Validation(String module, List<Row> parsed, List<Map<String, Object>> rows, int errors, int duplicates) {}
}
