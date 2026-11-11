package com.example.securehello.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password) {

    /** Keep the password out of logs: the default record toString() would print it. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=<redacted>]";
    }
}
