package com.themainthread.networkfaults;

public record QuoteError(String destination, String error, String cause) {
}
