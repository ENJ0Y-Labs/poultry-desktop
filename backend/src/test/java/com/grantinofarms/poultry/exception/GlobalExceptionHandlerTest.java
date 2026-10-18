package com.grantinofarms.poultry.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

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

    @Test
    void unsupportedMethodReturnsStableClientError() {
        var response = handler.methodNotAllowed(new HttpRequestMethodNotSupportedException("TRACE"));

        assertEquals(405, response.getStatusCode().value());
        assertEquals(
                Map.of(
                        "ok", false,
                        "error", Map.of(
                                "code", "METHOD_NOT_ALLOWED",
                                "message", "The HTTP method is not supported for this endpoint."
                        )
                ),
                response.getBody()
        );
    }

    @Test
    void unsupportedMediaTypeReturnsStableClientError() {
        var response = handler.unsupportedMediaType(new HttpMediaTypeNotSupportedException("text/plain"));

        assertEquals(415, response.getStatusCode().value());
        assertEquals(
                Map.of(
                        "ok", false,
                        "error", Map.of(
                                "code", "UNSUPPORTED_MEDIA_TYPE",
                                "message", "The request content type is not supported."
                        )
                ),
                response.getBody()
        );
    }
}
