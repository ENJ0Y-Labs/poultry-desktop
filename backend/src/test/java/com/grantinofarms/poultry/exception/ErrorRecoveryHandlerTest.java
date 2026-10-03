package com.grantinofarms.poultry.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;

import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorRecoveryHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void diskFullGetsActionableStorageError() {
        var response = handler.database(
                new DataAccessResourceFailureException(
                        "database or disk is full",
                        new SQLException("database or disk is full")
                )
        );

        assertEquals(507, response.getStatusCode().value());
        assertEquals(
                Map.of(
                        "ok", false,
                        "error", Map.of(
                                "code", "STORAGE_FULL",
                                "message", "There is not enough disk space to complete the operation."
                        )
                ),
                response.getBody()
        );
    }

    @Test
    void readOnlyDatabaseGetsPermissionGuidance() {
        var response = handler.database(
                new DataAccessResourceFailureException(
                        "attempt to write a readonly database",
                        new SQLException("attempt to write a readonly database")
                )
        );

        assertEquals(403, response.getStatusCode().value());
        assertEquals("DATABASE_READ_ONLY",
                ((Map<?, ?>) response.getBody()).get("error") instanceof Map<?, ?> error
                        ? error.get("code")
                        : null);
    }

    @Test
    void corruptDatabaseGetsRestoreGuidance() {
        var response = handler.database(
                new DataAccessResourceFailureException(
                        "database disk image is malformed",
                        new SQLException("database disk image is malformed")
                )
        );

        assertEquals(500, response.getStatusCode().value());
        assertEquals("DATABASE_CORRUPTED",
                ((Map<?, ?>) response.getBody()).get("error") instanceof Map<?, ?> error
                        ? error.get("code")
                        : null);
    }

    @Test
    void accessDeniedGetsPermissionGuidance() {
        var response = handler.accessDenied(
                new AccessDeniedException("poultry.db")
        );

        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
        assertEquals("STORAGE_ACCESS_DENIED",
                ((Map<?, ?>) response.getBody()).get("error") instanceof Map<?, ?> error
                        ? error.get("code")
                        : null);
    }

    @Test
    void fileSystemDiskFullGetsStorageError() {
        var exception = new FileSystemException("poultry.db", null, "No space left on device");
        var response = handler.fileSystem(exception);

        assertEquals(507, response.getStatusCode().value());
        assertEquals("STORAGE_FULL",
                ((Map<?, ?>) response.getBody()).get("error") instanceof Map<?, ?> error
                        ? error.get("code")
                        : null);
    }
    @Test
    void malformedRequestGetsReadableBadRequest() {
        var response = handler.unreadable(
                new HttpMessageNotReadableException("malformed JSON", new RuntimeException("parser detail"))
        );

        assertEquals(400, response.getStatusCode().value());
        assertEquals("INVALID_REQUEST_BODY",
                ((Map<?, ?>) response.getBody()).get("error") instanceof Map<?, ?> error
                        ? error.get("code")
                        : null);
    }

}
