package com.themainthread.barcode;

final class BarcodeInputException extends RuntimeException {
    private final String field;

    BarcodeInputException(String field, String message) {
        super(message);
        this.field = field;
    }

    String field() {
        return field;
    }
}
