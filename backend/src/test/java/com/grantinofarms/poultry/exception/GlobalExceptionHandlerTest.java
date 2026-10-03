package com.grantinofarms.poultry.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionKeepsDomainCodeAndMessage() {
        var response = handler.api(new ApiException(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_BIRDS",
                "The sale exceeds the available birds."
        ));

        assertEquals(409, response.getStatusCode().value());
        assertEquals(
                Map.of(
                        "ok", false,
                        "error", Map.of(
                                "code", "INSUFFICIENT_BIRDS",
                                "message", "The sale exceeds the available birds."
                        )
                ),
                response.getBody()
        );
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalDetails() {
        var response = handler.unexpected(new RuntimeException("SQL password=secret"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals(
                Map.of(
                        "ok", false,
                        "error", Map.of(
                                "code", "INTERNAL_ERROR",
                                "message", "The operation could not be completed."
                        )
                ),
                response.getBody()
        );
    }
}
