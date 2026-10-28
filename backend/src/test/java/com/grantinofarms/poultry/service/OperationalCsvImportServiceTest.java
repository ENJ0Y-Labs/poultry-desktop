package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.domain.BirdPopulationEvent;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.BirdPopulationRepository;
import com.grantinofarms.poultry.repository.DailyOperationsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationalCsvImportServiceTest {
    @Mock BatchService batches;
    @Mock DailyOperationsService dailyOperations;
    @Mock BirdPopulationService population;
    @Mock BirdPopulationRepository populationRepository;
    @Mock DailyOperationsRepository dailyRepository;

    private OperationalCsvImportService service;
    private final BatchResponse batch = new BatchResponse(
            "batch-id", "farm-id", "house-id", null, "B-2026-001", "BROILER",
            LocalDate.parse("2026-01-01"), 5, 10000L, "ACTIVE",
            "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new OperationalCsvImportService(batches, dailyOperations, population, populationRepository, dailyRepository);
        when(batches.list()).thenReturn(List.of(batch));
        when(dailyOperations.get(eq("batch-id"), any(LocalDate.class))).thenAnswer(call -> {
            LocalDate date = call.getArgument(1);
            return new DailyRecordResponse(null, "batch-id", date, 5, 0, 0,
                    List.of(), List.of(), 0L, List.of(), null);
        });
        when(populationRepository.findEvents("batch-id")).thenReturn(new java.util.ArrayList<>());
    }

    @Test
    void previewsValidMortalityWithoutWriting() {
        Map<String, Object> result = service.preview(new CsvImportRequest("mortality", List.of(
                Map.of("batch_code", "B-2026-001", "event_date", "2026-01-02",
                        "quantity", "2", "reason", "Disease", "notes", "Historical record"))));
        assertEquals(true, result.get("valid"));
        assertEquals(0, result.get("rejected"));
        verify(population, never()).addMortality(anyString(), any());
    }

    @Test
    void blocksPopulationThatWouldBecomeNegativeBeforeAnyWrite() {
        CsvImportRequest request = new CsvImportRequest("mortality", List.of(
                Map.of("batch_code", "B-2026-001", "event_date", "2026-01-02", "quantity", "4", "reason", "A", "notes", ""),
                Map.of("batch_code", "B-2026-001", "event_date", "2026-01-03", "quantity", "2", "reason", "B", "notes", "")));
        Map<String, Object> result = service.preview(request);
        assertEquals(false, result.get("valid"));
        assertEquals(1, result.get("rejected"));
        verify(population, never()).addMortality(anyString(), any());
    }

    @Test
    void importsThroughExistingPopulationServiceAndSkipsRepeatedFileEvents() {
        Map<String, String> row = Map.of("batch_code", "B-2026-001", "event_date", "2026-01-02",
                "quantity", "2", "reason", "Disease", "notes", "Same event");
        Map<String, Object> result = service.importRecords(new CsvImportRequest("mortality", List.of(row, row)));
        assertEquals(1, result.get("imported"));
        assertEquals(1, result.get("duplicates"));
        verify(population, times(1)).addMortality(eq("batch-id"), any(BirdPopulationEventRequest.class));
    }

    @Test
    void rejectsEventsBeforePlacementDate() {
        Map<String, Object> result = service.preview(new CsvImportRequest("culling", List.of(
                Map.of("batch_code", "B-2026-001", "event_date", "2025-12-31",
                        "quantity", "1", "reason", "Poor growth", "notes", ""))));
        assertEquals(false, result.get("valid"));
        verify(population, never()).addCulling(anyString(), any());
    }
    @Test
    void rejectsBackdatedEventThatInvalidatesALaterPopulationEvent() {
        when(populationRepository.findEvents("batch-id")).thenReturn(new java.util.ArrayList<>(List.of(
                new BirdPopulationEvent(LocalDate.parse("2026-01-03"), "CULLING", 3))));
        Map<String, Object> result = service.preview(new CsvImportRequest("mortality", List.of(
                Map.of("batch_code", "B-2026-001", "event_date", "2026-01-02",
                        "quantity", "3", "reason", "Historical mortality", "notes", ""))));
        assertEquals(false, result.get("valid"));
        assertEquals(1, result.get("rejected"));
        verify(population, never()).addMortality(anyString(), any());
    }
}
