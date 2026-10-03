package com.themainthread.barcode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class BarcodeService {
    private final BarcodeValidator validator;

    BarcodeService(BarcodeValidator validator) {
        this.validator = validator;
    }

    GeneratedBarcode generate(BarcodeType type, String value, String widthText, String heightText, String ecc) {
        validator.validate(type, value);
        int width = dimension("width", widthText, type.width());
        int height = dimension("height", heightText, type.height());
        Map<EncodeHintType, Object> hints = hints(type, ecc);
        BitMatrix matrix;
        try {
            MultiFormatWriter writer = new MultiFormatWriter();
            if (type.linear()) {
                BitMatrix minimum = writer.encode(value, type.format(), 0, 1, hints);
                int minimumWidth = minimum.getWidth() * 2;
                if (width < minimumWidth) {
                    throw new BarcodeInputException("width", "Use a width of at least " + minimumWidth
                            + " pixels for this value; the lab reserves two pixels per narrow module.");
                }
            }
            matrix = writer.encode(value, type.format(), width, height, hints);
        } catch (WriterException | IllegalArgumentException e) {
            throw new BarcodeInputException("value",
                    "The encoder cannot fit this value in " + type.label() + " with these settings. Shorten it"
                            + (type == BarcodeType.QR ? " or choose a lower QR error-correction level." : "."));
        }
        if (matrix.getWidth() > 1600 || matrix.getHeight() > 1600) {
            throw new BarcodeInputException("value", "The encoded symbol exceeds the lab's 1600-pixel image limit.");
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return new GeneratedBarcode(output.toByteArray(), matrix.getWidth(), matrix.getHeight());
        } catch (IOException e) {
            throw new IllegalStateException("Could not render the barcode PNG", e);
        }
    }

    private static int dimension(String field, String text, int defaultValue) {
        if (text == null) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(text);
            if (value >= 128 && value <= 1600) {
                return value;
            }
        } catch (NumberFormatException e) {
            // Use the same input error for malformed and out-of-range dimensions.
        }
        throw new BarcodeInputException(field, "Choose a whole number from 128 to 1600 pixels.");
    }

    private static Map<EncodeHintType, Object> hints(BarcodeType type, String ecc) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        if (type.linear()) {
            // ZXing treats this as total horizontal margin, split between both sides.
            hints.put(EncodeHintType.MARGIN, 40);
        } else {
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        }
        if (type != BarcodeType.QR && ecc != null) {
            throw new BarcodeInputException("ecc", "The ecc parameter is available only for QR codes.");
        }
        if (type == BarcodeType.QR) {
            hints.put(EncodeHintType.MARGIN, 4);
            try {
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.valueOf(ecc == null ? "M" : ecc));
            } catch (IllegalArgumentException e) {
                throw new BarcodeInputException("ecc", "Choose QR error correction L, M, Q or H.");
            }
        }
        return hints;
    }

    record GeneratedBarcode(byte[] png, int width, int height) {
    }
}
