package com.grantinofarms.poultry.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.sql.SQLException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException e) {
        log.warn("api_error code={} status={}", e.getCode(), e.getStatus().value());
        return error(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        log.warn("request_validation_failed");
        Map<String, String> fields = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "One or more fields are invalid.", fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<?> methodValidation(HandlerMethodValidationException e) {
        log.warn("request_method_validation_failed");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "One or more request values are invalid.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> unreadable(HttpMessageNotReadableException e) {
        log.warn("request_body_unreadable");
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", "The request body is malformed or contains an invalid value.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<?> missingParameter(MissingServletRequestParameterException e) {
        log.warn("request_parameter_missing parameter={}", e.getParameterName());
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "Required request parameter '" + e.getParameterName() + "' is missing.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<?> typeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("request_parameter_invalid parameter={}", e.getName());
        return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Request parameter '" + e.getName() + "' has an invalid value.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> integrity(DataIntegrityViolationException e) {
        log.warn("data_integrity_conflict");
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "The operation conflicts with existing farm data.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> accessDenied(AccessDeniedException e) {
        log.warn("storage_access_denied");
        return error(HttpStatus.FORBIDDEN, "STORAGE_ACCESS_DENIED",
                "The application does not have permission to access the required file or folder.");
    }

    @ExceptionHandler(FileSystemException.class)
    ResponseEntity<?> fileSystem(FileSystemException e) {
        if (isDiskFull(e)) {
            log.warn("storage_full");
            return error(HttpStatus.INSUFFICIENT_STORAGE, "STORAGE_FULL",
                    "There is not enough disk space to complete the operation.");
        }
        log.warn("file_system_error reason={}", safeReason(e));
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_OPERATION_FAILED",
                "The file operation could not be completed.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> database(DataAccessException e) {
        Throwable cause = rootCause(e);
        String detail = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase();
        if (detail.contains("database or disk is full") || detail.contains("disk is full")) {
            log.warn("storage_full");
            return error(HttpStatus.INSUFFICIENT_STORAGE, "STORAGE_FULL",
                    "There is not enough disk space to complete the operation.");
        }
        if (detail.contains("readonly") || detail.contains("read-only")) {
            log.warn("database_read_only");
            return error(HttpStatus.FORBIDDEN, "DATABASE_READ_ONLY",
                    "The database is read-only. Check file permissions and try again.");
        }
        if (detail.contains("malformed") || detail.contains("not a database")) {
            log.error("database_corrupt", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_CORRUPTED",
                    "The database appears to be damaged. Restore a valid backup before continuing.");
        }
        log.error("database_operation_failed", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_OPERATION_FAILED",
                "The database operation could not be completed.");
    }

    @ExceptionHandler(SQLException.class)
    ResponseEntity<?> sql(SQLException e) {
        String detail = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (detail.contains("database or disk is full") || detail.contains("disk is full")) {
            log.warn("storage_full");
            return error(HttpStatus.INSUFFICIENT_STORAGE, "STORAGE_FULL",
                    "There is not enough disk space to complete the operation.");
        }
        log.error("database_sql_error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_OPERATION_FAILED",
                "The database operation could not be completed.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) {
        log.error("unexpected_backend_error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The operation could not be completed.");
    }

    private boolean isDiskFull(FileSystemException e) {
        String reason = safeReason(e).toLowerCase();
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return reason.contains("space") || reason.contains("disk full")
                || message.contains("not enough space") || message.contains("disk full");
    }

    private String safeReason(FileSystemException e) {
        return e.getReason() == null ? "unknown" : e.getReason();
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return error(status, code, message, null);
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
            String code,
            String message,
            Map<String, String> fields
    ) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (fields != null && !fields.isEmpty()) {
            error.put("details", Map.of("fields", fields));
        }

        return ResponseEntity.status(status).body(Map.of(
                "ok", false,
                "error", error
        ));
    }
}
