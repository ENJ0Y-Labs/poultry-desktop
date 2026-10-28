package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthHistoryCsvImportServiceTest {
    @Mock BatchService batches;
    @Mock HealthManagementService health;
    @InjectMocks HealthHistoryCsvImportService importer;

    @Test
    void previewsHealthRowsWithoutWritingAndUsesBatchCode() {
        when(batches.list()).thenReturn(List.of(batch("B-2026-001", "ACTIVE")));
        when(health.health("batch-id")).thenReturn(List.of());
        Map<String, Object> result = importer.preview(new CsvImportRequest("health", List.of(
                Map.of("batch_code", "B-2026-001", "record_date", "2026-01-02",
                        "condition_problem", "Coughing", "description", "Several birds coughed",
                        "action", "Isolated affected birds"))));
        assertEquals(true, result.get("valid"));
        assertEquals(0, result.get("rejected"));
        verify(health, never()).addHealth(anyString(), any());
    }

    @Test
    void importsDrugThroughExistingServiceAndPreservesCostExpensePath() {
        when(batches.list()).thenReturn(List.of(batch("B-2026-001", "ACTIVE")));
        when(health.drugs("batch-id")).thenReturn(List.of());
        Map<String, Object> result = importer.importRecords(new CsvImportRequest("drugs", List.of(
                Map.of("batch_code", "B-2026-001", "record_date", "2026-01-02", "drug", "Drug A",
                        "quantity", "2", "cost_minor", "25000", "reason", "Treatment"))));
        assertEquals(1, result.get("imported"));
        verify(health).addDrug("batch-id", new DrugRecordRequest(LocalDate.parse("2026-01-02"), "Drug A", 2, 25000L, "Treatment"));
    }

    @Test
    void skipsExactVaccinationRetry() {
        when(batches.list()).thenReturn(List.of(batch("B-2026-001", "ACTIVE")));
        when(health.vaccinations("batch-id")).thenReturn(List.of(
                new VaccinationRecordResponse("v1", "batch-id", LocalDate.parse("2026-01-02"), "Newcastle", "1 ml per bird", 100, "Round 1")));
        Map<String, Object> result = importer.importRecords(new CsvImportRequest("vaccinations", List.of(
                Map.of("batch_code", "B-2026-001", "record_date", "2026-01-02", "vaccine", "Newcastle",
                        "dose", "1 ml per bird", "quantity", "100", "notes", "Round 1"))));
        assertEquals(0, result.get("imported"));
        assertEquals(1, result.get("duplicates"));
        verify(health, never()).addVaccination(anyString(), any());
    }

    @Test
    void rejectsUnknownBatchAndPrePlacementDateBeforeAnyWrites() {
        when(batches.list()).thenReturn(List.of(batch("B-2026-001", "ACTIVE")));
        Map<String, Object> preview = importer.preview(new CsvImportRequest("health", List.of(
                Map.of("batch_code", "UNKNOWN", "record_date", "2025-12-31",
                        "condition_problem", "Coughing", "description", "Several birds coughed",
                        "action", "Isolated affected birds"))));
        assertEquals(false, preview.get("valid"));
        assertEquals(1, preview.get("rejected"));
        verify(health, never()).addHealth(anyString(), any());
    }

    @Test
    void rejectsZeroQuantityAndZeroCostForDrugRecords() {
        when(batches.list()).thenReturn(List.of(batch("B-2026-001", "ACTIVE")));
        Map<String, Object> preview = importer.preview(new CsvImportRequest("drugs", List.of(
                Map.of("batch_code", "B-2026-001", "record_date", "2026-01-02", "drug", "Drug A",
                        "quantity", "0", "cost_minor", "0", "reason", "Treatment"))));
        assertEquals(false, preview.get("valid"));
        assertEquals(1, preview.get("rejected"));
        verify(health, never()).addDrug(anyString(), any());
    }

    @Test
    void rejectsSoldBatchWithoutWriting() {
        when(batches.list()).thenReturn(List.of(batch("B-2026-001", "SOLD")));
        Map<String, Object> preview = importer.preview(new CsvImportRequest("vaccinations", List.of(
                Map.of("batch_code", "B-2026-001", "record_date", "2026-01-02", "vaccine", "Newcastle",
                        "dose", "1 ml per bird", "quantity", "100", "notes", ""))));
        assertEquals(false, preview.get("valid"));
        verify(health, never()).addVaccination(anyString(), any());
    }


    @Test
    void rejectsHealthRecordBeforeBatchPlacementDate() {
        when(batches.list()).thenReturn(List.of(batch("B-2026-001", "ACTIVE")));
        Map<String, Object> preview = importer.preview(new CsvImportRequest("health", List.of(
                Map.of("batch_code", "B-2026-001", "record_date", "2025-12-31",
                        "condition_problem", "Coughing", "description", "Several birds coughed",
                        "action", "Isolated affected birds"))));
        assertEquals(false, preview.get("valid"));
        assertEquals(1, preview.get("rejected"));
        verify(health, never()).addHealth(anyString(), any());
    }

    private BatchResponse batch(String code, String status) {
        return new BatchResponse("batch-id", "farm-id", "house-id", null, code, "BROILER",
                LocalDate.parse("2026-01-01"), 100, 100000L, status,
                "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");
    }
}
