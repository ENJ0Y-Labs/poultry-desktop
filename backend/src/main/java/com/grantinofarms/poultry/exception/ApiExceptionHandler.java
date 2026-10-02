package com.grantinofarms.poultry.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> handle(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of(
                "ok", false,
                "error", Map.of("code", e.getCode(), "message", e.getMessage())
        ));
    }
}
