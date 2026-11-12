package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.BatchCreateRequest;
import com.grantinofarms.poultry.dto.BatchResponse;
import com.grantinofarms.poultry.dto.CsvImportRequest;
import com.grantinofarms.poultry.dto.HouseResponse;
import com.grantinofarms.poultry.dto.SupplierResponse;
import com.grantinofarms.poultry.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class BatchCsvImportService {
    private final BatchService batches;
    private final HouseService houses;
    private final SupplierService suppliers;

    public BatchCsvImportService(BatchService batches, HouseService houses, SupplierService suppliers) {
        this.batches = batches;
        this.houses = houses;
        this.suppliers = suppliers;
    }

    public Map<String, Object> preview(CsvImportRequest request) {
        return prepare(request).preview();
    }

    @Transactional
    public Map<String, Object> importRecords(CsvImportRequest request) {
        PreparedImport prepared = prepare(request);
        if (prepared.invalidRows() > 0) {
            throw rowError(prepared.firstInvalidRow(), "record", "Correct every invalid row before confirming. No batches were imported.");
        }

        List<Map<String, String>> created = new ArrayList<>();
        for (PreparedRow row : prepared.rows()) {
            BatchResponse batch = batches.create(row.request());
            Map<String, String> result = new LinkedHashMap<>();
            result.put("code", batch.code());
            result.put("type", batch.type());
            result.put("placementDate", batch.placementDate().toString());
            result.put("house", row.house().name());
            created.add(result);
        }
        return Map.of("module", "batches", "imported", created.size(), "duplicates", 0, "createdBatches", created);
    }

    private PreparedImport prepare(CsvImportRequest request) {
        if (request == null || request.module() == null || !"batches".equalsIgnoreCase(request.module().trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_MODULE_UNSUPPORTED",
                    "This endpoint only supports batch opening records.");
        }

        Map<String, HouseResponse> houseByCode = new LinkedHashMap<>();
        for (HouseResponse house : houses.list()) {
            if ("ACTIVE".equals(house.status())) houseByCode.put(house.code().trim().toUpperCase(Locale.ROOT), house);
        }
        Map<String, SupplierResponse> supplierByName = new LinkedHashMap<>();
        for (SupplierResponse supplier : suppliers.list()) {
            if ("ACTIVE".equals(supplier.status())) supplierByName.put(supplier.name().trim().toLowerCase(Locale.ROOT), supplier);
        }
        List<BatchResponse> existingBatches = batches.list();

        List<PreparedRow> preparedRows = new ArrayList<>();
        List<Map<String, Object>> previewRows = new ArrayList<>();
        Set<String> fingerprints = new HashSet<>();
        int invalid = 0;
        int firstInvalid = -1;

        for (int index = 0; index < request.records().size(); index++) {
            Map<String, String> row = request.records().get(index);
            int rowNumber = index + 2;
            List<String> errors = new ArrayList<>();
            requireOnly(row, Set.of("type", "placement_date", "house_code", "initial_bird_count",
                    "population_count_status", "supplier_name", "purchase_cost_recorded", "purchase_cost_naira"), rowNumber);

            String type = clean(row.get("type"));
            if (type != null) type = type.toUpperCase(Locale.ROOT);
            if (type == null || !List.of("LAYER", "BROILER", "TURKEY").contains(type)) errors.add("type must be LAYER, BROILER, or TURKEY.");

            LocalDate placementDate = null;
            try {
                placementDate = LocalDate.parse(clean(row.get("placement_date")));
            } catch (DateTimeParseException | NullPointerException exception) {
                errors.add("placement_date must use YYYY-MM-DD.");
            }

            String houseCode = clean(row.get("house_code"));
            HouseResponse house = houseCode == null ? null : houseByCode.get(houseCode.toUpperCase(Locale.ROOT));
            if (houseCode == null) errors.add("house_code is required.");
            else if (house == null) errors.add("No active house/pen has this code. Import or activate the house first.");

            int initialBirdCount = 0;
            try {
                initialBirdCount = Integer.parseInt(clean(row.get("initial_bird_count")));
                if (initialBirdCount <= 0) errors.add("initial_bird_count must be a positive whole number.");
            } catch (NumberFormatException | NullPointerException exception) {
                errors.add("initial_bird_count must be a positive whole number.");
            }

            String populationStatus = clean(row.get("population_count_status"));
            if (populationStatus == null || populationStatus.isBlank()) populationStatus = "ESTIMATED";
            else populationStatus = populationStatus.toUpperCase(Locale.ROOT);
            if (!List.of("ESTIMATED", "VERIFIED").contains(populationStatus)) {
                errors.add("population_count_status must be ESTIMATED or VERIFIED.");
            }

            String supplierName = clean(row.get("supplier_name"));
            SupplierResponse supplier = supplierName == null ? null : supplierByName.get(supplierName.toLowerCase(Locale.ROOT));
            if (supplierName != null && supplier == null) errors.add("No active supplier has this exact name.");

            Boolean purchaseCostRecorded = parseBoolean(clean(row.get("purchase_cost_recorded")));
            if (purchaseCostRecorded == null) errors.add("purchase_cost_recorded must be true or false.");
            Long purchaseCostMinor = null;
            String naira = clean(row.get("purchase_cost_naira"));
            if (Boolean.TRUE.equals(purchaseCostRecorded)) {
                try {
                    purchaseCostMinor = new BigDecimal(naira).setScale(2, RoundingMode.UNNECESSARY)
                            .movePointRight(2).longValueExact();
                    if (purchaseCostMinor < 0) errors.add("purchase_cost_naira cannot be negative.");
                } catch (Exception exception) {
                    errors.add("purchase_cost_naira is required when purchase_cost_recorded is true and must have at most two decimal places.");
                }
            } else if (naira != null) {
                errors.add("Leave purchase_cost_naira blank when purchase_cost_recorded is false.");
            }

            BatchCreateRequest batchRequest = null;
            if (errors.isEmpty()) {
                batchRequest = new BatchCreateRequest(type, placementDate, house.id(), initialBirdCount,
                        supplier == null ? null : supplier.id(), purchaseCostRecorded, purchaseCostMinor, populationStatus);
                String fingerprint = String.join("|", type, placementDate.toString(), house.id(),
                        Integer.toString(initialBirdCount), supplier == null ? "" : supplier.id(),
                        purchaseCostMinor == null ? "" : purchaseCostMinor.toString(), populationStatus);
                if (!fingerprints.add(fingerprint)) errors.add("Duplicate batch opening row within this file.");
                final String resolvedType = type;
                final LocalDate resolvedPlacementDate = placementDate;
                final int resolvedBirdCount = initialBirdCount;
                final String resolvedSupplierId = supplier == null ? null : supplier.id();
                final Long resolvedPurchaseCostMinor = purchaseCostMinor;
                final String resolvedPopulationStatus = populationStatus;
                boolean existingMatch = existingBatches.stream().anyMatch(existing ->
                        existing.type().equals(resolvedType)
                                && existing.placementDate().equals(resolvedPlacementDate)
                                && existing.houseId().equals(house.id())
                                && existing.initialBirdCount() == resolvedBirdCount
                                && java.util.Objects.equals(existing.supplierId(), resolvedSupplierId)
                                && java.util.Objects.equals(existing.purchaseCostMinor(), resolvedPurchaseCostMinor)
                                && existing.populationCountStatus().equals(resolvedPopulationStatus));
                if (existingMatch) errors.add("A batch with the same opening details already exists. Review existing batches; no automatic skip is performed.");
            }

            boolean valid = errors.isEmpty();
            if (!valid) {
                invalid++;
                if (firstInvalid < 0) firstInvalid = rowNumber;
            } else {
                preparedRows.add(new PreparedRow(batchRequest, house));
            }
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("rowNumber", rowNumber);
            preview.put("valid", valid);
            preview.put("duplicate", errors.stream().anyMatch(error -> error.toLowerCase(Locale.ROOT).contains("duplicate")));
            preview.put("errors", errors);
            preview.put("record", row);
            preview.put("resolvedHouse", house == null ? "" : house.name() + " (" + house.code() + ")");
            preview.put("resolvedSupplier", supplier == null ? "" : supplier.name());
            previewRows.add(preview);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("module", "batches");
        summary.put("totalRows", request.records().size());
        summary.put("validRows", request.records().size() - invalid);
        summary.put("invalidRows", invalid);
        summary.put("duplicates", previewRows.stream().filter(row -> Boolean.TRUE.equals(row.get("duplicate"))).count());
        summary.put("rows", previewRows);
        return new PreparedImport(preparedRows, summary, invalid, firstInvalid);
    }

    private void requireOnly(Map<String, String> row, Set<String> allowed, int rowNumber) {
        for (String key : row.keySet()) {
            if (!allowed.contains(key)) throw rowError(rowNumber, key, "Unexpected column.");
        }
    }

    private Boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return null;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ApiException rowError(int row, String column, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "CSV_IMPORT_ROW_INVALID",
                "CSV row " + row + ", column '" + column + "': " + message);
    }

    private record PreparedRow(BatchCreateRequest request, HouseResponse house) {}
    private record PreparedImport(List<PreparedRow> rows, Map<String, Object> preview, int invalidRows, int firstInvalidRow) {}
}
