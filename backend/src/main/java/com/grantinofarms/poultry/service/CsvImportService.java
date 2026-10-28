package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.CsvImportRequest;
import com.grantinofarms.poultry.dto.CustomerCreateRequest;
import com.grantinofarms.poultry.dto.SupplierCreateRequest;
import com.grantinofarms.poultry.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CsvImportService {
    private final CustomerService customers;
    private final SupplierService suppliers;

    public CsvImportService(CustomerService customers, SupplierService suppliers) {
        this.customers = customers;
        this.suppliers = suppliers;
    }

    @Transactional
    public Map<String, Object> importRecords(CsvImportRequest request) {
        String module = request.module().trim().toLowerCase(Locale.ROOT);
        if (!module.equals("customers") && !module.equals("suppliers")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_MODULE_UNSUPPORTED",
                    "Only customer and supplier imports are currently supported.");
        }
        Set<String> names = new HashSet<>();
        List<Map<String, String>> records = request.records();
        for (int i = 0; i < records.size(); i++) {
            Map<String, String> row = records.get(i);
            String name = clean(row.get("name"));
            if (name == null) {
                throw rowError(i + 2, "name", "Name is required.");
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw rowError(i + 2, "name", "Duplicate name in this import file.");
            }
            if (module.equals("customers")) {
                requireOnly(row, Set.of("name", "phone", "notes"), i + 2);
            } else {
                requireOnly(row, Set.of("name", "phone", "email", "address", "notes"), i + 2);
            }
        }

        // Service calls join this transaction. A duplicate or validation failure rolls back
        // the entire import, including audit rows, rather than leaving a partial import.
        int imported = 0;
        for (int i = 0; i < records.size(); i++) {
            Map<String, String> row = records.get(i);
            if (module.equals("customers")) {
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
        return Map.of("module", module, "imported", imported, "duplicates", 0, "rejected", 0);
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
