package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.CsvImportRequest;
import com.grantinofarms.poultry.dto.CustomerCreateRequest;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.dto.HouseResponse;
import com.grantinofarms.poultry.dto.SupplierCreateRequest;
import com.grantinofarms.poultry.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CsvImportService {
    private final CustomerService customers;
    private final SupplierService suppliers;
    private final HouseService houses;

    public CsvImportService(CustomerService customers, SupplierService suppliers, HouseService houses) {
        this.customers = customers;
        this.suppliers = suppliers;
        this.houses = houses;
    }

    @Transactional
    public Map<String, Object> importRecords(CsvImportRequest request) {
        String module = request.module().trim().toLowerCase(Locale.ROOT);
        if (!Set.of("customers", "suppliers", "houses").contains(module)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_MODULE_UNSUPPORTED",
                    "Only house/pen, customer, and supplier imports are currently supported.");
        }

        List<Map<String, String>> records = request.records();
        Set<String> names = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < records.size(); i++) {
            Map<String, String> row = records.get(i);
            String name = clean(row.get("name"));
            if (name == null) throw rowError(i + 2, "name", "Name is required.");
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw rowError(i + 2, "name", "Duplicate name in this import file.");
            }

            if (module.equals("houses")) {
                requireOnly(row, Set.of("name", "code", "notes"), i + 2);
                String code = clean(row.get("code"));
                if (code == null) throw rowError(i + 2, "code", "House/pen code is required.");
                if (!codes.add(code.toUpperCase(Locale.ROOT))) {
                    throw rowError(i + 2, "code", "Duplicate house/pen code in this import file.");
                }
            } else if (module.equals("customers")) {
                requireOnly(row, Set.of("name", "phone", "notes"), i + 2);
            } else {
                requireOnly(row, Set.of("name", "phone", "email", "address", "notes"), i + 2);
            }
        }

        // Validate all existing-record collisions before writing any row.
        // Exact house matches are idempotent duplicates; matching only one key or
        // matching keys with different details is a conflict, never an implicit update.
        Map<String, HouseResponse> existingHouseByCode = new HashMap<>();
        Map<String, HouseResponse> existingHouseByName = new HashMap<>();
        if (module.equals("houses")) {
            for (HouseResponse existing : houses.list()) {
                existingHouseByCode.put(existing.code().toUpperCase(Locale.ROOT), existing);
                existingHouseByName.put(existing.name().toLowerCase(Locale.ROOT), existing);
            }
            for (int i = 0; i < records.size(); i++) {
                Map<String, String> row = records.get(i);
                String code = clean(row.get("code")).toUpperCase(Locale.ROOT);
                String name = clean(row.get("name"));
                HouseResponse codeMatch = existingHouseByCode.get(code);
                HouseResponse nameMatch = existingHouseByName.get(name.toLowerCase(Locale.ROOT));
                if (codeMatch != null || nameMatch != null) {
                    boolean exact = codeMatch != null && codeMatch == nameMatch
                            && codeMatch.name().equals(name)
                            && equalNullable(codeMatch.notes(), clean(row.get("notes")));
                    if (!exact) {
                        throw rowError(i + 2, codeMatch != null ? "code" : "name",
                                "Conflicts with an existing house/pen. No rows were imported.");
                    }
                }
            }
        }

        int imported = 0;
        int duplicates = 0;
        for (int i = 0; i < records.size(); i++) {
            Map<String, String> row = records.get(i);
            if (module.equals("houses")) {
                String code = clean(row.get("code")).toUpperCase(Locale.ROOT);
                if (existingHouseByCode.containsKey(code)) {
                    duplicates++;
                    continue;
                }
                houses.create(new HouseCreateRequest(clean(row.get("name")), code, clean(row.get("notes"))));
            } else if (module.equals("customers")) {
                if (customers.list().stream().anyMatch(item -> item.name().equalsIgnoreCase(clean(row.get("name"))))) {
                    throw rowError(i + 2, "name", "A customer with this name already exists. No rows were imported.");
                }
                customers.create(new CustomerCreateRequest(clean(row.get("name")), clean(row.get("phone")), clean(row.get("notes"))));
            } else {
                if (suppliers.list().stream().anyMatch(item -> item.name().equalsIgnoreCase(clean(row.get("name"))))) {
                    throw rowError(i + 2, "name", "A supplier with this name already exists. No rows were imported.");
                }
                suppliers.create(new SupplierCreateRequest(clean(row.get("name")), clean(row.get("phone")),
                        clean(row.get("email")), clean(row.get("address")), clean(row.get("notes"))));
            }
            imported++;
        }
        return Map.of("module", module, "imported", imported, "duplicates", duplicates, "rejected", 0);
    }

    private boolean equalNullable(String left, String right) {
        return java.util.Objects.equals(clean(left), clean(right));
    }

    private void requireOnly(Map<String, String> row, Set<String> allowed, int rowNumber) {
        for (String key : row.keySet()) {
            if (!allowed.contains(key)) throw rowError(rowNumber, key, "Unexpected column.");
        }
    }

    private ApiException rowError(int row, String column, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "CSV_IMPORT_ROW_INVALID",
                "CSV row " + row + ", column '" + column + "': " + message);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}