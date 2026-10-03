package com.themainthread.barcode;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class BarcodeResourceTest {
    @ParameterizedTest
    @CsvSource({
            "qr,QR_CODE,https://www.the-main-thread.com/",
            "pdf417,PDF_417,TICKET|TMT-2026|ATTENDEE=JAVA-DEVELOPER|ACCESS=DAY-1",
            "aztec,AZTEC,TRAIN:ICE1001:MUC-BER:2026-10-03",
            "code128,CODE_128,ORD-2026-104829",
            "code39,CODE_39,PART-4711-A",
            "codabar,CODABAR,A123456789B",
            "ean13,EAN_13,4006381333931",
            "itf,ITF,12345678901234"
    })
    void roundTripsEveryFormatThroughPng(String type, BarcodeFormat format, String value) throws Exception {
        byte[] png = given().queryParam("value", value)
                .when().get("/barcodes/{type}", type)
                .then().statusCode(200).contentType("image/png").header("Cache-Control", "no-store")
                .extract().asByteArray();
        assertDecoded(png, format, value);
    }

    @ParameterizedTest
    @ValueSource(strings = { "qr", "pdf417", "aztec" })
    void preservesUtf8Text(String type) throws Exception {
        String value = "Grüße aus München — Java ☕";
        BarcodeFormat format = switch (type) {
            case "qr" -> BarcodeFormat.QR_CODE;
            case "pdf417" -> BarcodeFormat.PDF_417;
            default -> BarcodeFormat.AZTEC;
        };
        byte[] png = given().queryParam("value", value).when().get("/barcodes/{type}", type)
                .then().statusCode(200).extract().asByteArray();
        assertDecoded(png, format, value);
    }

    @ParameterizedTest
    @ValueSource(strings = { "L", "M", "Q", "H" })
    void roundTripsEachQrCorrectionLevel(String ecc) throws Exception {
        String value = "https://quarkus.io/?source=barcode-lab";
        byte[] png = given().queryParam("value", value).queryParam("ecc", ecc).when().get("/barcodes/qr")
                .then().statusCode(200).extract().asByteArray();
        assertDecoded(png, BarcodeFormat.QR_CODE, value);
    }

    @ParameterizedTest
    @CsvSource({
            "ean13,HELLO MARKUS,exactly 13 ASCII digits",
            "ean13,400638133393,exactly 13 ASCII digits",
            "ean13,4006381333932,check digit must be 1",
            "ean13,４００６３８１３３３９３１,ASCII digits",
            "itf,12345,even number",
            "itf,123A,ASCII digits",
            "code39,part-4711,base-alphabet",
            "code39,PART_4711,base-alphabet",
            "code39,PART*4711,base-alphabet",
            "code128,Grüße,printable ASCII",
            "codabar,123456,requires A-D",
            "codabar,A123E,requires A-D",
            "codabar,A12X3B,requires A-D"
    })
    void returnsUsefulContractErrors(String type, String value, String detail) {
        given().queryParam("value", value).when().get("/barcodes/{type}", type)
                .then().statusCode(400).contentType("application/problem+json")
                .body("status", equalTo(400)).body("field", equalTo("value"))
                .body("detail", containsString(detail));
    }

    @Test
    void doesNotTrimOrUppercaseIdentifiers() throws Exception {
        String value = "part-4711 ";
        byte[] png = given().queryParam("value", value).when().get("/barcodes/code128")
                .then().statusCode(200).extract().asByteArray();
        assertDecoded(png, BarcodeFormat.CODE_128, value);
    }

    @Test
    void rejectsMissingAndUnknownInputs() {
        given().when().get("/barcodes/qr").then().statusCode(400).body("field", equalTo("value"));
        given().queryParam("value", " ").when().get("/barcodes/qr").then().statusCode(400);
        given().queryParam("value", "hello").when().get("/barcodes/no-such-format")
                .then().statusCode(400).body("field", equalTo("type"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "-1", "0", "127", "1601", "999999999999999999", "hello" })
    void boundsRequestedDimensions(String width) {
        given().queryParam("value", "hello").queryParam("width", width).when().get("/barcodes/qr")
                .then().statusCode(400).body("field", equalTo("width"));
    }

    @Test
    void rejectsAnUndersizedLinearImage() {
        given().queryParam("value", "ORD-2026-104829").queryParam("width", 128)
                .when().get("/barcodes/code128").then().statusCode(400)
                .body("field", equalTo("width")).body("detail", containsString("at least"));
    }

    @Test
    void validatesCorrectionSettings() {
        given().queryParam("value", "hello").queryParam("ecc", "X").when().get("/barcodes/qr")
                .then().statusCode(400).body("field", equalTo("ecc"));
        given().queryParam("value", "ORD-42").queryParam("ecc", "H").when().get("/barcodes/code128")
                .then().statusCode(400).body("field", equalTo("ecc"));
    }

    @Test
    void separatesApplicationLimitsFromEncoderCapacity() {
        given().queryParam("value", "A".repeat(65)).when().get("/barcodes/code128")
                .then().statusCode(400).body("detail", containsString("at most 64"));
        given().queryParam("value", "a".repeat(2049)).when().get("/barcodes/qr")
                .then().statusCode(400).body("detail", containsString("at most 2048"));
        given().contentType("text/plain; charset=UTF-8").body("é".repeat(1100)).when().post("/barcodes/qr")
                .then().statusCode(400).body("detail", containsString("2048 UTF-8 bytes"));
        given().contentType("text/plain; charset=UTF-8").body("a".repeat(1800)).queryParam("ecc", "H")
                .when().post("/barcodes/qr")
                .then().statusCode(400).body("detail", containsString("encoder cannot fit"));
        given().contentType("text/plain; charset=UTF-8").body("é".repeat(800)).when().post("/barcodes/pdf417")
                .then().statusCode(400).body("detail", containsString("encoder cannot fit"));
    }

    @Test
    void acceptsUtf8InThePostBody() throws Exception {
        String value = "Grüße aus München — Java ☕";
        byte[] png = given().contentType("text/plain; charset=UTF-8").body(value)
                .when().post("/barcodes/qr").then().statusCode(200).contentType("image/png")
                .extract().asByteArray();
        assertDecoded(png, BarcodeFormat.QR_CODE, value);
    }

    @Test
    void validatesEanCheckDigitsIncludingZero() throws Exception {
        // Independently calculated GS1 weighted sums: 89 -> 1; 130 -> 0.
        for (String value : List.of("4006381333931", "1234567890180")) {
            byte[] png = given().queryParam("value", value).when().get("/barcodes/ean13")
                    .then().statusCode(200).extract().asByteArray();
            assertDecoded(png, BarcodeFormat.EAN_13, value);
        }
    }

    @Test
    void offersTheGalleryAndCatalog() {
        given().when().get("/gallery").then().statusCode(200).contentType("text/html")
                .body(containsString("Eight different contracts"))
                .body(containsString("data-format=\"ean13\""))
                .body(containsString("/gallery.js"));
        given().when().get("/").then().statusCode(200).body(containsString("Barcode Lab"));
        given().when().get("/barcodes").then().statusCode(200).contentType("application/json")
                .body("", hasSize(8)).body("id", hasItems("qr", "aztec", "pdf417", "ean13", "itf"));
    }

    private static void assertDecoded(byte[] png, BarcodeFormat format, String value) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image, "HTTP response must be a readable PNG");
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(format));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.RETURN_CODABAR_START_END, Boolean.TRUE);
        Result decoded = new MultiFormatReader().decode(bitmap, hints);
        assertEquals(format, decoded.getBarcodeFormat());
        assertEquals(value, decoded.getText());
    }
}
