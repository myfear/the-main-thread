package com.pixel;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ImageFilterResourceTest {
    static byte[] image(int width, int height, String format) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, ((x * 13 % 256) << 16) | ((y * 17 % 256) << 8) | 140);
            }
        }
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    @Test
    void convertsPngAndJpegOnAVirtualThread() throws Exception {
        for (String format : new String[] { "png", "jpeg" }) {
            byte[] result = given().multiPart("image", "input." + format, image(53, 37, format))
                    .multiPart("blockSize", "8").post("/api/filter/pixelate").then()
                    .statusCode(200).contentType("image/png").header("X-Execution-Virtual", "true")
                    .header("X-Image-Width", "48").header("X-Image-Height", "32")
                    .extract().asByteArray();
            var decoded = ImageIO.read(new ByteArrayInputStream(result));
            assertNotNull(decoded);
            assertEquals(48, decoded.getWidth());
            assertEquals(32, decoded.getHeight());
        }
    }

    @Test
    void defaultsBlockSizeAndAcceptsOnePixel() throws Exception {
        given().multiPart("image", "input.png", image(16, 16, "png"))
                .post("/api/filter/pixelate").then().statusCode(200).header("X-Image-Width", "16");
        given().multiPart("image", "input.png", image(1, 1, "png")).multiPart("blockSize", "1")
                .post("/api/filter/pixelate").then().statusCode(200).header("X-Image-Width", "1");
    }

    @Test
    void rejectsInvalidImagesAndSettings() throws Exception {
        byte[] input = image(32, 32, "png");
        for (String block : new String[] { "0", "-1", "129", "33", "abc" }) {
            given().multiPart("image", "input.png", input).multiPart("blockSize", block)
                    .post("/api/filter/pixelate").then().statusCode(400);
        }
        given().multiPart("blockSize", "8").post("/api/filter/pixelate").then().statusCode(400);
        for (byte[] bad : new byte[][] { new byte[0], "not an image".getBytes(), Arrays.copyOf(input, 40),
                image(32, 32, "gif") }) {
            given().multiPart("image", "input.png", bad)
                    .post("/api/filter/pixelate").then().statusCode(400);
        }
        // A failed decode must not consume one of the conversion slots permanently.
        given().multiPart("image", "input.png", input).post("/api/filter/pixelate").then().statusCode(200);
    }

    @Test
    void boundsDecodedPixelsAndRequestBytes() throws Exception {
        given().multiPart("image", "large.png", image(2001, 2000, "png"))
                .post("/api/filter/pixelate").then().statusCode(413);
        given().multiPart("image", "large.png", new byte[16 * 1024 * 1024])
                .post("/api/filter/pixelate").then().statusCode(413);
    }
}
