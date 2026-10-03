package com.themainthread.barcode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.google.zxing.BarcodeFormat;

public enum BarcodeType {
    QR("qr", "QR Code", BarcodeFormat.QR_CODE, 320, 320,
            "https://www.the-main-thread.com/", "URLs and text for camera readers",
            "UTF-8 text; four-module quiet zone; QR error correction L, M, Q or H.", 2048),
    PDF417("pdf417", "PDF417", BarcodeFormat.PDF_417, 640, 240,
            "TICKET|TMT-2026|ATTENDEE=JAVA-DEVELOPER|ACCESS=DAY-1", "Document and ticket payloads",
            "UTF-8 text in a stacked rectangular symbol; reader must support PDF417.", 2048),
    AZTEC("aztec", "Aztec", BarcodeFormat.AZTEC, 320, 320,
            "TRAIN:ICE1001:MUC-BER:2026-10-03", "Compact transport-style ticket data",
            "UTF-8 text with a central finder pattern; this sample is not a railway ticket protocol.", 2048),
    CODE128("code128", "Code 128", BarcodeFormat.CODE_128, 640, 160,
            "ORD-2026-104829", "Internal order and inventory identifiers",
            "Lab policy: printable ASCII only, at most 64 characters; plain Code 128, without GS1 rules.", 64),
    CODE39("code39", "Code 39", BarcodeFormat.CODE_39, 640, 160,
            "PART-4711-A", "Identifiers for existing industrial readers",
            "Base alphabet only: A-Z, 0-9, space and - . $ / + %; lab limit 40 characters.", 40),
    CODABAR("codabar", "Codabar", BarcodeFormat.CODABAR, 640, 160,
            "A123456789B", "Interoperability with systems using Codabar",
            "Explicit A-D guards; interior digits or - $ : / . +; lab limit 40 characters including guards.", 40),
    EAN13("ean13", "EAN-13", BarcodeFormat.EAN_13, 640, 160,
            "4006381333931", "Retail-style product identifiers",
            "Exactly 13 ASCII digits and a valid GS1 check digit; no product allocation is verified.", 13),
    ITF("itf", "Interleaved 2 of 5", BarcodeFormat.ITF, 640, 160,
            "12345678901234", "Paired numeric identifiers",
            "Even number of ASCII digits, at most 80; generic ITF, without ITF-14 business or print rules.", 80);

    private final String id;
    private final String label;
    private final BarcodeFormat format;
    private final int width;
    private final int height;
    private final String example;
    private final String goodFit;
    private final String constraint;
    private final int maxLength;

    BarcodeType(String id, String label, BarcodeFormat format, int width, int height, String example,
            String goodFit, String constraint, int maxLength) {
        this.id = id;
        this.label = label;
        this.format = format;
        this.width = width;
        this.height = height;
        this.example = example;
        this.goodFit = goodFit;
        this.constraint = constraint;
        this.maxLength = maxLength;
    }

    public static BarcodeType fromId(String id) {
        for (BarcodeType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        throw new BarcodeInputException("type", "Choose qr, pdf417, aztec, code128, code39, codabar, ean13 or itf.");
    }

    public boolean linear() {
        return this != QR && this != PDF417 && this != AZTEC;
    }

    public String imageUrl() {
        return "/barcodes/" + id + "?value=" + URLEncoder.encode(example, StandardCharsets.UTF_8);
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public BarcodeFormat format() {
        return format;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String example() {
        return example;
    }

    public String goodFit() {
        return goodFit;
    }

    public String constraint() {
        return constraint;
    }

    public int maxLength() {
        return maxLength;
    }
}
