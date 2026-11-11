package com.example.securehello;

import com.example.securehello.web.dto.LoginRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRequestTest {

    @Test
    void toStringDoesNotLeakThePassword() {
        LoginRequest request = new LoginRequest("test", "123456");

        assertEquals("test", request.username());
        assertEquals("123456", request.password());

        String text = request.toString();
        assertFalse(text.contains("123456"), text);
        assertTrue(text.contains("username=test"), text);
        assertTrue(text.contains("redacted"), text);
    }
}
