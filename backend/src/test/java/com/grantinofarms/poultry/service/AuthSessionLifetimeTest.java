package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.LoginRequest;
import com.grantinofarms.poultry.repository.SessionRepository;
import com.grantinofarms.poultry.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionLifetimeTest {
    private UserRepository users;
    private SessionRepository sessions;
    private PasswordHasher passwords;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        sessions = mock(SessionRepository.class);
        passwords = mock(PasswordHasher.class);
        service = new AuthService(users, sessions, passwords);
        when(users.findByIdentifier("owner@example.com")).thenReturn(Map.of(
                "username", "owner",
                "id", "owner-1",
                "username", "owner",
                "email", "owner@example.com",
                "full_name", "Farm Owner",
                "role", "OWNER",
                "status", "ACTIVE",
                "password_hash", "hash"
        ));
        when(passwords.matches("correct-password", "hash")).thenReturn(true);
    }

    @Test
    void sessionOnlyLoginUsesTwelveHourServerExpiry() {
        Instant before = Instant.now();
        service.login(new LoginRequest("owner@example.com", "correct-password", false), "127.0.0.1");
        Instant after = Instant.now();

        ArgumentCaptor<String> expiry = ArgumentCaptor.forClass(String.class);
        verify(sessions).insert(anyString(), eq("owner-1"), anyString(), anyString(), expiry.capture());
        assertExpiryBetween(expiry.getValue(), before.plus(Duration.ofHours(12)), after.plus(Duration.ofHours(12)));
    }

    @Test
    void persistentLoginUsesThirtyDayServerExpiry() {
        Instant before = Instant.now();
        service.login(new LoginRequest("owner@example.com", "correct-password", true), "127.0.0.1");
        Instant after = Instant.now();

        ArgumentCaptor<String> expiry = ArgumentCaptor.forClass(String.class);
        verify(sessions).insert(anyString(), eq("owner-1"), anyString(), anyString(), expiry.capture());
        assertExpiryBetween(expiry.getValue(), before.plus(Duration.ofDays(30)), after.plus(Duration.ofDays(30)));
    }

    private void assertExpiryBetween(String actualValue, Instant earliest, Instant latest) {
        Instant actual = Instant.parse(actualValue);
        assertThat(actual).isBetween(earliest, latest);
    }
}
