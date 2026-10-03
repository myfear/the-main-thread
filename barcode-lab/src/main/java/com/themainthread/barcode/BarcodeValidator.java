package com.themainthread.barcode;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class BarcodeValidator {
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");
    private static final Pattern PRINTABLE_ASCII = Pattern.compile("[\\x20-\\x7E]+");
    private static final Pattern CODE39 = Pattern.compile("[A-Z0-9 .$/+%\\-]+");
    private static final Pattern CODABAR = Pattern.compile("[A-D][0-9\\-$:/+.]+[A-D]");

    void validate(BarcodeType type, String value) {
        if (value == null || value.isBlank()) {
            fail("Supply a non-blank value.");
        }
        if (value.length() > type.maxLength()) {
            fail(type.label() + " accepts at most " + type.maxLength() + " characters in this lab.");
        }
        if (!type.linear() && value.getBytes(StandardCharsets.UTF_8).length > 2048) {
            fail("The lab accepts at most 2048 UTF-8 bytes; encoder capacity can be lower.");
        }
        switch (type) {
            case CODE128 -> require(PRINTABLE_ASCII.matcher(value).matches(),
                    "Code 128 in this lab accepts printable ASCII only (space through ~).");
            case CODE39 -> require(CODE39.matcher(value).matches(),
                    "Code 39 accepts A-Z, 0-9, space and - . $ / + % in base-alphabet mode.");
            case CODABAR -> require(CODABAR.matcher(value).matches(),
                    "Codabar requires A-D start/stop characters and interior digits or - $ : / . +.");
            case EAN13 -> {
                require(value.length() == 13 && DIGITS.matcher(value).matches(),
                        "EAN-13 requires exactly 13 ASCII digits, including the check digit.");
                int expected = ean13CheckDigit(value.substring(0, 12));
                require(value.charAt(12) - '0' == expected,
                        "EAN-13 check digit must be " + expected + " for these first 12 digits.");
            }
            case ITF -> {
                require(DIGITS.matcher(value).matches(), "ITF accepts ASCII digits only.");
                require(value.length() % 2 == 0, "ITF requires an even number of digits.");
            }
            default -> {
            }
        }
    }

    static int ean13CheckDigit(String firstTwelveDigits) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (firstTwelveDigits.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return (10 - sum % 10) % 10;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            fail(message);
        }
    }

    private static void fail(String message) {
        throw new BarcodeInputException("value", message);
    }
}
