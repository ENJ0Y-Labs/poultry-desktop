package com.grantinofarms.poultry.exception;

import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerProductionClassTest {

    @Test
    void loadsTheProductionExceptionHandlerInsteadOfATestSourceDuplicate() {
        URL classResource = GlobalExceptionHandler.class.getResource("GlobalExceptionHandler.class");

        assertNotNull(classResource, "The production GlobalExceptionHandler class must be on the test classpath.");

        String location = classResource.toExternalForm().replace('\\', '/');
        assertTrue(
                location.contains("/target/classes/"),
                () -> "Expected GlobalExceptionHandler to load from Maven's production classes, but got: " + location
        );
        assertFalse(
                location.contains("/target/test-classes/"),
                () -> "GlobalExceptionHandler must not be loaded from test classes: " + location
        );
    }
}
