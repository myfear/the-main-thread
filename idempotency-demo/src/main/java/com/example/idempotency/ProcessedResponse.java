package com.example.idempotency;

public record ProcessedResponse(int status, String body) {
}