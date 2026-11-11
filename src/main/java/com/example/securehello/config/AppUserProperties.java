package com.example.securehello.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo account loaded from {@code application.yml}. Configuration stores only a BCrypt
 * hash; the demo password appears in README/tests for reproducible verification, and is
 * never stored in configuration or compared as plaintext.
 *
 * @param username     login name of the demo account
 * @param passwordHash BCrypt hash of the demo account password
 */
@ConfigurationProperties(prefix = "app.security")
public record AppUserProperties(String username, String passwordHash) {
}
