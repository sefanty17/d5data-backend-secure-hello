package com.example.securehello;

import com.example.securehello.config.AppUserProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the password-storage requirement: the configured value must be a
 * BCrypt hash with cost 10, and it must verify against the documented demo
 * password (the demo password itself lives in README/tests, not in config).
 */
@SpringBootTest
class PasswordStorageTest {

    @Autowired
    private AppUserProperties properties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void demoPasswordIsStoredAsBcryptHashWithCost10() {
        String hash = properties.passwordHash();

        assertNotNull(hash);
        assertNotEquals("123456", hash);
        // $2a$ = BCrypt, 10 = cost factor. README claims cost 10; pin it.
        assertTrue(hash.startsWith("$2a$10$"),
                "configured value must be a BCrypt hash with cost 10 ($2a$10$), got: " + hash);
        assertTrue(hash.matches("^\\$2a\\$10\\$[./A-Za-z0-9]{53}$"),
                "configured value must be a well-formed BCrypt hash, got: " + hash);
        assertTrue(passwordEncoder.matches("123456", hash),
                "hash must verify the documented demo password");
    }
}
