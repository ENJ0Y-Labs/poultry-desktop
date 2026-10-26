package com.grantinofarms.poultry.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.core.MethodParameter;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    void validationFailureReturnsBadRequestAndFieldDetails() throws Exception {
        var binding = new BeanPropertyBindingResult(new ValidationTarget(), "request");
        binding.addError(new FieldError("request", "name", "must not be blank"));
        var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("validationTarget", String.class);
        var exception = new MethodArgumentNotValidException(new MethodParameter(method, 0), binding);

        var response = handler.validation(exception);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(
                Map.of(
                        "ok", false,
                        "error", Map.of(
                                "code", "VALIDATION_ERROR",
                                "message", "One or more fields are invalid.",
                                "details", Map.of("fields", Map.of("name", "must not be blank"))
                        )
                ),
                response.getBody()
        );
    }

    @Test
    void dataIntegrityFailureReturnsConflict() {
        var response = handler.integrity(new DataIntegrityViolationException("duplicate key"));

        assertEquals(409, response.getStatusCode().value());
        assertEquals(
                Map.of(
                        "ok", false,
                        "error", Map.of(
                                "code", "DATA_CONFLICT",
                                "message", "The operation conflicts with existing farm data."
                        )
                ),
                response.getBody()
        );
    }

    @Test
    void missingRouteReturnsNotFound() {
        var response = handler.notFound(new NoResourceFoundException(HttpMethod.GET, "/missing"));

        assertEquals(404, response.getStatusCode().value());
        assertEquals(
                Map.of(
                        "ok", false,
                        "error", Map.of(
                                "code", "NOT_FOUND",
                                "message", "The requested endpoint was not found."
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

    @SuppressWarnings("unused")
    private void validationTarget(String name) {}

    private static final class ValidationTarget {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
