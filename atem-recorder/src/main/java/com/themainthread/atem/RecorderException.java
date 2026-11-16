package com.themainthread.atem;

final class RecorderException extends RuntimeException {
    private final int status;

    RecorderException(int status, String message) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }
}
