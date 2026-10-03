package com.grantinofarms.poultry.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
    void validationExceptionReturnsFieldDetails() {
        var target = new ValidRequest();
        var binding = new BeanPropertyBindingResult(target, "request");
        binding.addError(new FieldError("request", "quantity", "Quantity must be greater than zero."));
        var exception = new MethodArgumentNotValidException(null, binding);

        var response = handler.validation(exception);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(false, response.getBody().get("ok"));

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("VALIDATION_ERROR", error.get("code"));
        assertEquals("One or more fields are invalid.", error.get("message"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) error.get("details");
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) details.get("fields");
        assertEquals("Quantity must be greater than zero.", fields.get("quantity"));
    }

    @Test
    void malformedBodyUsesStableErrorCode() {
        var response = handler.unreadable(new HttpMessageNotReadableException("bad body"));

        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertEquals("INVALID_REQUEST_BODY", error.get("code"));
    }

    private static final class ValidRequest {
        @Valid
        @NotBlank
        private String value;
    }
}
