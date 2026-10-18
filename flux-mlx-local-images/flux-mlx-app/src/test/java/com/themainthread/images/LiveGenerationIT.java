package com.themainthread.images;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
@EnabledIfEnvironmentVariable(named = "FLUX_LIVE_TEST", matches = "true")
public class LiveGenerationIT {
    @Test
    void generatesTwoSizesWithTheActualLocalModels() throws Exception {
        Path evidence = Path.of("..", "eval", "mflux");
        Files.createDirectories(evidence);
        var caption = given().contentType("application/json").body(Map.of("brief",
                "An editorial illustration of a small blue ceramic camera on a warm wooden desk, "
                        + "a red apple beside it, soft morning light, clean background, no text or logos."))
                .post("/api/captions").then().statusCode(200).extract().response();
        Files.writeString(evidence.resolve("caption-live.json"), caption.asString());
        String prompt = caption.path("caption");
        assertTrue(prompt.length() > 40);
        for (int size : new int[] { 512, 1024 }) {
            String id = given().contentType("application/json")
                    .body(Map.of("prompt", prompt, "width", size, "height", size, "seed", 42))
                    .post("/api/images").then().statusCode(202).extract().path("id");
            Instant deadline = Instant.now().plusSeconds(330);
            io.restassured.response.Response result;
            String state;
            do {
                Thread.sleep(500);
                result = given().get("/api/jobs/" + id).then().statusCode(200).extract().response();
                state = result.path("state");
            } while ((state.equals("QUEUED") || state.equals("RUNNING")) && Instant.now().isBefore(deadline));
            Files.writeString(evidence.resolve("live-" + size + ".json"), result.asString());
            assertEquals("SUCCEEDED", state, result.asString());
            byte[] png = given().get("/api/jobs/" + id + "/image").then().statusCode(200)
                    .contentType("image/png").extract().asByteArray();
            var image = ImageIO.read(new ByteArrayInputStream(png));
            assertEquals(size, image.getWidth());
            assertEquals(size, image.getHeight());
            Files.write(evidence.resolve("live-" + size + ".png"), png);
        }
    }
}
