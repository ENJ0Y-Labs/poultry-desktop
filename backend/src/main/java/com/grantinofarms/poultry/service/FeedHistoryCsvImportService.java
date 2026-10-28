package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.domain.FeedCostCalculator;
import com.grantinofarms.poultry.domain.FeedPurchaseLot;
import com.grantinofarms.poultry.domain.FeedUsageEvent;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.FeedRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Historical feed imports deliberately reuse FeedService for writes. Preview is read-only
 * and checks dated FIFO balances before any transaction is confirmed.
 */
@Service
public class FeedHistoryCsvImportService {
    private final FeedService feedService;
    private final FeedRepository feedRepository;
    private final BatchService batches;
    private final SupplierService suppliers;

    public FeedHistoryCsvImportService(FeedService feedService, FeedRepository feedRepository,
                                       BatchService batches, SupplierService suppliers) {
        this.feedService = feedService;
        this.feedRepository = feedRepository;
        this.batches = batches;
        this.suppliers = suppliers;
    }

    public Map<String, Object> preview(CsvImportRequest request) {
        String module = request.module() == null ? "" : request.module().trim();
        if (!List.of("feed-purchases", "feed-usage").contains(module)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMPORT_MODULE",
                    "Supported feed modules are feed-purchases and feed-usage.");
        }

        List<FeedTypeResponse> feedTypes = feedService.types();
        List<BatchResponse> batchList = batches.list();
        List<SupplierResponse> supplierList = suppliers.list();
        Map<String, FeedTypeResponse> typesByName = new HashMap<>();
        for (FeedTypeResponse type : feedTypes) {
            typesByName.put(normalize(type.name()), type);
        }
        Map<String, BatchResponse> batchesByCode = new HashMap<>();
        for (BatchResponse batch : batchList) {
            batchesByCode.put(normalize(batch.code()), batch);
        }
        Map<String, SupplierResponse> suppliersByName = new HashMap<>();
        for (SupplierResponse supplier : supplierList) {
            suppliersByName.put(normalize(supplier.name()), supplier);
        }

        List<ParsedRow> parsed = new ArrayList<>();
        List<Map<String, Object>> rowResults = new ArrayList<>();
        Set<String> fingerprints = new HashSet<>();
        int duplicates = 0;
        int rejected = 0;
        int index = 0;

        for (Map<String, String> raw : request.records()) {
            index++;
            List<String> errors = new ArrayList<>();
            try {
                ParsedRow row = parse(module, raw, index, typesByName, batchesByCode, suppliersByName, errors);
                if (row != null && errors.isEmpty()) {
                    String fingerprint = row.fingerprint();
                    if (!fingerprints.add(fingerprint) || row.isExistingDuplicate(feedRepository)) {
                        row.duplicate = true;
                        duplicates++;
                    } else {
                        parsed.add(row);
                    }
                    rowResults.add(row.toResult(index, errors));
                } else {
                    rejected++;
                    rowResults.add(result(index, false, false, errors));
                }
            } catch (RuntimeException e) {
                rejected++;
                errors.add(safeMessage(e));
                rowResults.add(result(index, false, false, errors));
            }
        }

        // Respect FeedService's dated transaction rules. A purchase cannot be inserted
        // before an already-recorded usage or purchase for the same feed type.
        for (ParsedRow row : parsed) {
            LocalDate latestPurchase = feedRepository.latestPurchaseDate(row.feedType.id());
            LocalDate latestUsage = feedRepository.latestUsageDate(row.feedType.id());
            if (row.date.isBefore(latestPurchase == null ? row.date : latestPurchase)
                    || row.module.equals("feed-purchases") && latestUsage != null && row.date.isBefore(latestUsage)) {
                row.errors.add("Date conflicts with existing feed history; existing feed services reject this backdated transaction.");
            }
            if (row.module.equals("feed-usage")) {
                LocalDate placement = row.batch.placementDate();
                if (row.date.isBefore(placement)) row.errors.add("Usage date is before the batch placement date.");
                if (!"ACTIVE".equals(row.batch.status())) row.errors.add("Feed usage requires an ACTIVE batch.");
                if (!isApplicable(row.feedType.applicableType(), row.batch.type())) {
                    row.errors.add("Feed type is not configured for this batch type.");
                }
            }
        }

        // Simulate each historical usage date independently. The calculator receives only
        // purchases and usage dated on or before that date, so a future purchase cannot fund
        // an earlier consumption event.
        Set<String> invalidStockRows = new HashSet<>();
        Map<String, List<ParsedRow>> byType = new HashMap<>();
        for (ParsedRow row : parsed) {
            if (row.module.equals("feed-usage")) byType.computeIfAbsent(row.feedType.id(), ignored -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<String, List<ParsedRow>> entry : byType.entrySet()) {
            List<ParsedRow> usages = entry.getValue();
            usages.sort(Comparator.comparing((ParsedRow r) -> r.date).thenComparingInt(r -> r.rowNumber));
            for (ParsedRow usage : usages) {
                try {
                    List<FeedPurchaseLot> purchaseLots = new ArrayList<>(feedRepository.findPurchases(entry.getKey(), usage.date));
                    List<FeedUsageEvent> usageEvents = new ArrayList<>(feedRepository.findUsages(entry.getKey(), usage.date));
                    for (ParsedRow candidate : parsed) {
                        if (!candidate.feedType.id().equals(entry.getKey()) || candidate.date.isAfter(usage.date)) continue;
                        Instant order = Instant.parse("2000-01-01T00:00:00Z").plusSeconds(candidate.rowNumber);
                        if (candidate.module.equals("feed-purchases")) {
                            purchaseLots.add(new FeedPurchaseLot("import-purchase-" + candidate.rowNumber,
                                    candidate.feedType.id(), candidate.date, candidate.quantityMilli,
                                    candidate.costMinor, order));
                        } else {
                            usageEvents.add(new FeedUsageEvent("import-usage-" + candidate.rowNumber,
                                    candidate.batch.id(), candidate.feedType.id(), candidate.date,
                                    candidate.quantityMilli, order));
                        }
                    }
                    FeedCostCalculator.calculate(purchaseLots, usageEvents, usage.date);
                } catch (IllegalArgumentException | ArithmeticException e) {
                    usage.errors.add("Insufficient dated FIFO stock or invalid feed balance at this usage date.");
                    invalidStockRows.add(usage.fingerprint());
                }
            }
        }

        // Rebuild row results with simulation/date validation attached.
        rowResults.clear();
        rejected = 0;
        for (ParsedRow row : parsed) {
            if (!row.errors.isEmpty()) rejected++;
            rowResults.add(row.toResult(row.rowNumber, row.errors));
        }
        boolean valid = rejected == 0 && rowResults.size() == request.records().size();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("module", module);
        response.put("valid", valid);
        response.put("rows", rowResults);
        response.put("totalRows", request.records().size());
        response.put("validRows", (int) rowResults.stream().filter(r -> Boolean.TRUE.equals(r.get("valid"))).count());
        response.put("rejected", rejected);
        response.put("duplicates", duplicates);
        return response;
    }

    @Transactional
    public Map<String, Object> importRecords(CsvImportRequest request) {
        Map<String, Object> preview = preview(request);
        if (!Boolean.TRUE.equals(preview.get("valid"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FEED_IMPORT_VALIDATION_FAILED",
                    "Feed import validation failed. Correct the preview errors before confirming.");
        }
        String module = request.module().trim();
        List<FeedTypeResponse> types = feedService.types();
        List<BatchResponse> batchList = batches.list();
        List<SupplierResponse> supplierList = suppliers.list();
        Map<String, FeedTypeResponse> typesByName = new HashMap<>();
        types.forEach(t -> typesByName.put(normalize(t.name()), t));
        Map<String, BatchResponse> batchesByCode = new HashMap<>();
        batchList.forEach(b -> batchesByCode.put(normalize(b.code()), b));
        Map<String, SupplierResponse> suppliersByName = new HashMap<>();
        supplierList.forEach(s -> suppliersByName.put(normalize(s.name()), s));

        List<ParsedRow> rows = new ArrayList<>();
        Set<String> fingerprints = new HashSet<>();
        int rowNumber = 0;
        for (Map<String, String> raw : request.records()) {
            rowNumber++;
            List<String> errors = new ArrayList<>();
            ParsedRow row = parse(module, raw, rowNumber, typesByName, batchesByCode, suppliersByName, errors);
            if (row == null || !errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,
                    "FEED_IMPORT_VALIDATION_FAILED", "Invalid feed CSV row " + rowNumber + ".");
            if (fingerprints.add(row.fingerprint()) && !row.isExistingDuplicate(feedRepository)) rows.add(row);
        }
        rows.sort(Comparator.comparing((ParsedRow r) -> r.date)
                .thenComparingInt(r -> r.module.equals("feed-purchases") ? 0 : 1)
                .thenComparingInt(r -> r.rowNumber));

        int importedPurchases = 0;
        int importedUsage = 0;
        for (ParsedRow row : rows) {
            if (row.module.equals("feed-purchases")) {
                feedService.purchase(new FeedPurchaseRequest(row.feedType.id(), row.supplier == null ? null : row.supplier.id(),
                        row.date, row.quantity, row.feedType.unit(), row.costMinor));
                importedPurchases++;
            } else {
                feedService.use(row.batch.id(), new FeedUsageRequest(row.feedType.id(), row.date,
                        row.quantity, row.feedType.unit(), row.reason));
                importedUsage++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("importedPurchases", importedPurchases);
        result.put("importedUsage", importedUsage);
        result.put("skippedDuplicates", (int) preview.get("duplicates"));
        result.put("message", "Feed history imported through the existing feed services.");
        return result;
    }

    private ParsedRow parse(String module, Map<String, String> raw, int rowNumber,
                            Map<String, FeedTypeResponse> types, Map<String, BatchResponse> batches,
                            Map<String, SupplierResponse> suppliers, List<String> errors) {
        String feedTypeName = value(raw, "feed_type_name");
        FeedTypeResponse type = types.get(normalize(feedTypeName));
        if (type == null) errors.add("Unknown feed_type_name; use the exact existing feed type name.");
        else if (!"ACTIVE".equals(type.status())) errors.add("Feed type is archived.");

        LocalDate date = null;
        try { date = LocalDate.parse(value(raw, module.equals("feed-purchases") ? "purchase_date" : "usage_date")); }
        catch (RuntimeException e) { errors.add("Date must use YYYY-MM-DD."); }

        BigDecimal quantity = null;
        long quantityMilli = 0L;
        try {
            quantity = new BigDecimal(value(raw, "quantity"));
            quantityMilli = quantity.setScale(3, RoundingMode.UNNECESSARY).movePointRight(3).longValueExact();
            if (quantityMilli <= 0) errors.add("Quantity must be greater than zero.");
        } catch (RuntimeException e) { errors.add("Quantity must be positive with at most 3 decimal places."); }

        if (type != null && !type.unit().equalsIgnoreCase(value(raw, "unit"))) {
            errors.add("Unit must exactly match the configured feed type unit: " + type.unit() + ".");
        }
        SupplierResponse supplier = null;
        BatchResponse batch = null;
        long costMinor = 0L;
        String reason = clean(value(raw, "reason"));
        if (module.equals("feed-purchases")) {
            String supplierName = clean(value(raw, "supplier_name"));
            if (supplierName != null) {
                supplier = suppliers.get(normalize(supplierName));
                if (supplier == null || !"ACTIVE".equals(supplier.status())) errors.add("Unknown or inactive supplier_name.");
            }
            try {
                BigDecimal naira = new BigDecimal(value(raw, "total_cost_naira"));
                costMinor = naira.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
                if (costMinor <= 0) errors.add("total_cost_naira must be greater than zero with at most 2 decimal places.");
            } catch (RuntimeException e) { errors.add("total_cost_naira must be a valid naira amount with at most 2 decimal places."); }
        } else {
            batch = batches.get(normalize(value(raw, "batch_code")));
            if (batch == null) errors.add("Unknown batch_code; use the existing human-readable batch code.");
            if (reason == null) reason = "Historical feed usage";
        }

        if (type == null || date == null || quantity == null || !errors.isEmpty()) return null;
        return new ParsedRow(module, rowNumber, type, supplier, batch, date, quantity, quantityMilli, costMinor, reason);
    }

    private String value(Map<String, String> row, String key) {
        String value = row == null ? null : row.get(key);
        return value == null ? "" : value.trim();
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private boolean isApplicable(String scope, String batchType) {
        return "BOTH".equals(scope) || scope.equals(batchType);
    }
    private String safeMessage(RuntimeException e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? "Invalid row." : e.getMessage();
    }
    private Map<String, Object> result(int row, boolean valid, boolean duplicate, List<String> errors) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rowNumber", row); result.put("valid", valid); result.put("duplicate", duplicate);
        result.put("errors", List.copyOf(errors)); return result;
    }

    private static final class ParsedRow {
        final String module;
        final int rowNumber;
        final FeedTypeResponse feedType;
        final SupplierResponse supplier;
        final BatchResponse batch;
        final LocalDate date;
        final BigDecimal quantity;
        final long quantityMilli;
        final long costMinor;
        final String reason;
        final List<String> errors = new ArrayList<>();
        boolean duplicate;
        ParsedRow(String module, int rowNumber, FeedTypeResponse feedType, SupplierResponse supplier,
                  BatchResponse batch, LocalDate date, BigDecimal quantity, long quantityMilli,
                  long costMinor, String reason) {
            this.module=module; this.rowNumber=rowNumber; this.feedType=feedType; this.supplier=supplier;
            this.batch=batch; this.date=date; this.quantity=quantity; this.quantityMilli=quantityMilli;
            this.costMinor=costMinor; this.reason=reason;
        }
        String fingerprint() {
            return module.equals("feed-purchases")
                    ? module + "|" + feedType.id() + "|" + (supplier == null ? "" : supplier.id()) + "|" + date + "|" + quantityMilli + "|" + costMinor
                    : module + "|" + batch.id() + "|" + feedType.id() + "|" + date + "|" + quantityMilli + "|" + reason;
        }
        boolean isExistingDuplicate(FeedRepository repository) {
            return module.equals("feed-purchases")
                    ? repository.purchaseExists(feedType.id(), supplier == null ? null : supplier.id(), date, quantityMilli, costMinor)
                    : repository.usageExists(batch.id(), feedType.id(), date, quantityMilli, reason);
        }
        Map<String, Object> toResult(int row, List<String> errors) {
            return Map.of("rowNumber", row, "valid", errors.isEmpty(), "duplicate", duplicate,
                    "errors", List.copyOf(errors));
        }
    }
}
