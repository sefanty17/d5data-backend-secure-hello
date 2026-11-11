package com.example.securehello.web.dto;

public record ErrorResponse(int status, String error, String message) {
}
