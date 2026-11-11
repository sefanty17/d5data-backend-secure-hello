package com.example.securehello.web.dto;

public record LoginResponse(String username, boolean authenticated, String message) {

    public static LoginResponse authenticated(String username) {
        return new LoginResponse(username, true, "Login successful");
    }
}
