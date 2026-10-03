package com.grantinofarms.poultry.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PasswordHasherSecurityTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashesNeverContainThePlaintextAndVerifyCorrectly() {
        String password = "correct horse battery staple";
        String encoded = hasher.hash(password);

        assertNotEquals(password, encoded);
        assertTrue(encoded.startsWith("pbkdf2$600000$"));
        assertTrue(hasher.matches(password, encoded));
        assertFalse(hasher.matches("wrong password", encoded));
    }

    @Test
    void eachPasswordGetsAUniqueSalt() {
        String password = "same password";

        assertNotEquals(hasher.hash(password), hasher.hash(password));
    }

    @Test
    void malformedHashesAreRejected() {
        assertFalse(hasher.matches("password", "not-a-password-hash"));
        assertFalse(hasher.matches("password", "pbkdf2$600000$invalid$invalid"));
    }
}
