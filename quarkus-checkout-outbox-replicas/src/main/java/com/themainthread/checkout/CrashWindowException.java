package com.themainthread.checkout;

public class CrashWindowException extends RuntimeException {

    private final CrashPoint point;

    public CrashWindowException(CrashPoint point) {
        super("Simulated crash after " + point);
        this.point = point;
    }

    public CrashPoint point() {
        return point;
    }
}
