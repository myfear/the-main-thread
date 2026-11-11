package com.themainthread.filterlab;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;

import javax.imageio.ImageIO;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
class FilterResourceTest {

    @Test
    void catalogExposesTheTwoDeepDives() {
        given()
                .get("/api/filters")
                .then()
                .statusCode(200)
                .body(containsString("\"gaussian\""))
                .body(containsString("\"dither\""))
                .body(containsString("\"deepDive\":true"));
    }

    @Test
    void theoryEndpointsDescribeTheKernelAndMatrices() {
        given()
                .queryParam("radius", 5)
                .get("/api/theory/gaussian")
                .then()
                .statusCode(200)
                .body("taps", greaterThan(1))
                .body("sigma", notNullValue())
                .body("weights[0]", notNullValue());

        given()
                .get("/api/theory/dither")
                .then()
                .statusCode(200)
                .body(containsString("\"halftone6\""))
                .body(containsString("\"magic4\""));
    }

    @Test
    void sampleIsAPng() {
        byte[] png = given()
                .get("/api/sample.png")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("image/png"))
                .extract().asByteArray();
        assertTrue(png.length > 1000);
    }

    @Test
    void renderReturnsFilteredPngAndHeaders() {
        byte[] source = given().get("/api/sample.png").asByteArray();
        byte[] result = given()
                .multiPart("image", "sample.png", source, "image/png")
                .multiPart("filter", "dither")
                .multiPart("settings", "{\"matrix\":\"halftone6\",\"levels\":2,\"colorDither\":true}")
                .post("/api/render")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("image/png"))
                .header("X-Render-Millis", notNullValue())
                .header("X-Image-Width", "1280")
                .extract().asByteArray();
        assertFalse(Arrays.equals(source, result));
    }

    @Test
    void renderWithoutAnImageFallsBackToTheTestFrame() {
        given()
                .multiPart("filter", "sepia")
                .multiPart("settings", "{}")
                .post("/api/render")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("image/png"));
    }

    @Test
    void unknownFilterIsAJsonBadRequest() {
        given()
                .multiPart("filter", "does-not-exist")
                .post("/api/render")
                .then()
                .statusCode(400)
                .header("Content-Type", containsString("application/json"))
                .body("error", containsString("Unknown filter"));
    }

    @Test
    void brokenImageIsAJsonBadRequest() {
        given()
                .multiPart("image", "broken.png", "not an image".getBytes(), "image/png")
                .multiPart("filter", "gaussian")
                .post("/api/render")
                .then()
                .statusCode(400)
                .header("Content-Type", containsString("application/json"))
                .body("error", containsString("not a supported image"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "gaussian", "dither", "quantize", "unsharp", "posterize", "sepia", "grayscale",
            "solarize", "contrast", "invert" })
    void everyCatalogFilterRenders(String filter) throws Exception {
        byte[] png = given().multiPart("filter", filter).post("/api/render")
                .then().statusCode(200).header("X-Image-Width", "1280")
                .header("X-Image-Height", "800").extract().asByteArray();
        assertEquals(1280, ImageIO.read(new ByteArrayInputStream(png)).getWidth());
    }

    @ParameterizedTest
    @ValueSource(strings = { "[]", "null", "{", "{} {}", "{\"radius\":true}", "{\"radius\":null}",
            "{\"radius\":\"NaN\"}", "{\"radius\":\"Infinity\"}", "{\"raduis\":4}" })
    void invalidSettingsAreJsonBadRequests(String settings) {
        given().multiPart("filter", "gaussian").multiPart("settings", settings).post("/api/render")
                .then().statusCode(400).header("Content-Type", containsString("application/json"))
                .body("error", notNullValue());
    }

    @Test
    void missingFilterIsRejected() {
        given().multiPart("settings", "{}").post("/api/render")
                .then().statusCode(400).body("error", containsString("required"));
    }

    @Test
    void emptyUploadDoesNotSilentlyUseTheSample() {
        given().multiPart("image", "empty.png", new byte[0], "image/png")
                .multiPart("filter", "invert").post("/api/render")
                .then().statusCode(400).body("error", containsString("non-empty"));
    }

    @Test
    void overBudgetImageIsRejectedBeforeDecodingPixels() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // A compressed 25 MP PNG fits under the byte limit but exceeds the pixel budget.
        ImageIO.write(new BufferedImage(5000, 5000,
                BufferedImage.TYPE_BYTE_BINARY), "png", output);
        given().multiPart("image", "large.png", output.toByteArray(), "image/png")
                .multiPart("filter", "invert").post("/api/render")
                .then().statusCode(400).body("error", containsString("24 megapixels"));
    }

    @Test
    void oversizedRequestIsRejected() throws Exception {
        // A known Content-Length and 100-continue let the server reject before the body is sent.
        // A streaming multipart client can instead see a broken pipe on early rejection.
        URI uri = URI.create(RestAssured.baseURI + ":" + RestAssured.port + "/api/render");
        HttpRequest request = HttpRequest.newBuilder(uri).expectContinue(true)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[13 * 1024 * 1024])).build();
        try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            assertEquals(413, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
        }
    }

    @Test
    void orientedJpegIsRotatedThroughTheHttpEndpoint() throws Exception {
        File file = new File("src/test/resources/images/oriented.jpg");
        BufferedImage original = ImageIO.read(file);
        byte[] png = given().multiPart("image", file, "image/jpeg")
                .multiPart("filter", "gaussian").multiPart("settings", "{\"radius\":0}")
                .post("/api/render").then().statusCode(200).extract().asByteArray();
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(original.getHeight(), result.getWidth());
        assertEquals(original.getWidth(), result.getHeight());
        assertEquals(original.getRGB(0, 0), result.getRGB(result.getWidth() - 1, 0));
    }

    @Test
    void quantizeDoesNotExposeTheBrokenM2DiffusionOption() {
        given().multiPart("filter", "quantize").multiPart("settings", "{\"dither\":true}")
                .post("/api/render").then().statusCode(400).body("error", containsString("Unknown setting"));
    }
}
