package com.themainthread.networkfaults;

public class CarrierHttpException extends RuntimeException {

    public CarrierHttpException(int status) {
        super("Carrier returned HTTP " + status);
    }
}
