package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.CsvImportRequest;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.dto.HouseResponse;
import com.grantinofarms.poultry.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceHouseTest {
    @Mock CustomerService customers;
    @Mock SupplierService suppliers;
    @Mock HouseService houses;
    @InjectMocks CsvImportService service;

    @Test
    void importsHouseAndPenThroughExistingService() {
        when(houses.list()).thenReturn(List.of());
        Map<String, Object> result = service.importRecords(new CsvImportRequest("houses", List.of(
                Map.of("name", "Brooder House", "code", "h-001", "notes", "New birds"))));
        assertEquals(1, result.get("imported"));
        assertEquals(0, result.get("duplicates"));
        verify(houses).create(new HouseCreateRequest("Brooder House", "H-001", "New birds"));
    }

    @Test
    void skipsExactExistingHouseOnRetry() {
        when(houses.list()).thenReturn(List.of(house("Brooder House", "H-001", "New birds")));
        Map<String, Object> result = service.importRecords(new CsvImportRequest("houses", List.of(
                Map.of("name", "Brooder House", "code", "h-001", "notes", "New birds"))));
        assertEquals(0, result.get("imported"));
        assertEquals(1, result.get("duplicates"));
        verify(houses, never()).create(any());
    }

    @Test
    void rejectsExistingCodeWithDifferentDetailsBeforeWriting() {
        when(houses.list()).thenReturn(List.of(house("Brooder House", "H-001", "Old notes")));
        ApiException error = assertThrows(ApiException.class, () -> service.importRecords(new CsvImportRequest("houses", List.of(
                Map.of("name", "Brooder House", "code", "H-001", "notes", "Changed notes"),
                Map.of("name", "Grower House", "code", "H-002", "notes", "")))));
        assertTrue(error.getMessage().contains("CSV row 2"));
        verify(houses, never()).create(any());
    }

    @Test
    void rejectsDuplicateCodesWithinFileBeforeWriting() {
        ApiException error = assertThrows(ApiException.class, () -> service.importRecords(new CsvImportRequest("houses", List.of(
                Map.of("name", "House A", "code", "H-1", "notes", ""),
                Map.of("name", "House B", "code", "h-1", "notes", "")))));
        assertTrue(error.getMessage().contains("CSV row 3"));
        assertTrue(error.getMessage().contains("code"));
        verify(houses, never()).create(any());
    }

    @Test
    void rejectsMissingCodeBeforeWriting() {
        ApiException error = assertThrows(ApiException.class, () -> service.importRecords(new CsvImportRequest("houses", List.of(
                Map.of("name", "House A", "code", "", "notes", "")))));
        assertTrue(error.getMessage().contains("CSV row 2"));
        assertTrue(error.getMessage().contains("code"));
        verify(houses, never()).create(any());
    }

    private HouseResponse house(String name, String code, String notes) {
        return new HouseResponse("house-id", "farm-id", name, code, notes, "ACTIVE", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");
    }
}
