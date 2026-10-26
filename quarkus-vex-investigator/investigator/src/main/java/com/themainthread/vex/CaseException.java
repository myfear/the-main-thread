package com.themainthread.vex;

final class CaseException extends RuntimeException {
    final int code;

    CaseException(int code, String message) {
        super(message);
        this.code = code;
    }
}
