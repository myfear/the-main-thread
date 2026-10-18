package com.themainthread.images;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(value = LocalModelServers.class, restrictToAnnotatedClass = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ImageApiTest {
    @Test
    @Order(1)
    void preparesIndependentCaptionsAndRejectsTruncatedResponses() {
        given().contentType("application/json").body(Map.of("brief", "A blue camera"))
                .post("/api/captions").then().statusCode(200)
                .body("caption", equalTo("A blue ceramic camera on a warm wooden desk."));
        var request = LocalModelServers.LAST_CHAT.get();
        assertEquals("qwen2.5:7b", request.path("model").asText());
        assertEquals(320, request.path("options").path("num_predict").asInt());
        assertTrue(request.path("messages").toString().contains("A blue camera"));
        given().contentType("application/json").body(Map.of("brief", "Another camera"))
                .post("/api/captions").then().statusCode(200);
        assertEquals(2, LocalModelServers.LAST_CHAT.get().path("messages").size());
        given().contentType("application/json").body(Map.of("brief", "truncate"))
                .post("/api/captions").then().statusCode(502);
    }

    @Test
    @Order(2)
    void generatesAPngAndSavesTheExactSamplingSettings() throws Exception {
        String prompt = "--output elsewhere; a blue camera, $(touch should-not-exist)";
        String id = submit(prompt, 512, 512, 42);
        await(id, "SUCCEEDED");
        byte[] bytes = given().get("/api/jobs/" + id + "/image").then().statusCode(200)
                .contentType("image/png").extract().asByteArray();
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertEquals(512, image.getWidth());
        assertEquals(512, image.getHeight());
        var request = lastCommand();
        assertEquals("flux3-image-klein", request.getProperty("--model"));
        assertEquals("16", request.getProperty("--steps"));
        assertEquals("42", request.getProperty("--seed"));
        assertEquals(prompt, request.getProperty("--prompt"));
        var saved = LocalModelServers.JSON.readTree(Files.readString(LocalModelServers.output.resolve(id + ".json")));
        assertEquals("SUCCEEDED", saved.path("state").asText());
        assertEquals(prompt, saved.path("prompt").asText());
    }

    @Test
    @Order(3)
    void rejectsInvalidPromptsAndDimensionsBeforeSubmission() {
        given().contentType("application/json").body(Map.of("prompt", " ", "width", 512, "height", 512, "seed", 0))
                .post("/api/images").then().statusCode(400);
        given().contentType("application/json").body(Map.of("prompt", "Camera", "width", 513, "height", 512, "seed", 0))
                .post("/api/images").then().statusCode(400);
        given().contentType("application/json").body(Map.of("prompt", "Camera", "width", 2048, "height", 512, "seed", -1))
                .post("/api/images").then().statusCode(400);
        given().get("/api/jobs/00000000-0000-0000-0000-000000000000").then().statusCode(404);
    }

    @Test
    @Order(4)
    void reportsBackendAndInvalidImageFailures() throws Exception {
        String rejected = submit("fail-backend", 512, 512, 1);
        await(rejected, "FAILED");
        given().get("/api/jobs/" + rejected + "/image").then().statusCode(409);
        String invalid = submit("bad-image", 512, 512, 1);
        await(invalid, "FAILED");
        given().get("/api/jobs/" + invalid + "/image").then().statusCode(409);
    }

    @Test
    @Order(5)
    void serializesGenerationAndRejectsAFullQueue() throws Exception {
        String first = submit("hold-image", 512, 512, 1);
        Instant deadline = Instant.now().plusSeconds(2);
        while (!Files.exists(LocalModelServers.output.resolve("entered")) && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
        }
        assertTrue(Files.exists(LocalModelServers.output.resolve("entered")));
        try {
            String second = submit("Second image", 512, 512, 2);
            String third = submit("Third image", 512, 512, 3);
            given().contentType("application/json").body(Map.of("prompt", "Fourth image", "width", 512, "height", 512, "seed", 4))
                    .post("/api/images").then().statusCode(429);
            Files.writeString(LocalModelServers.output.resolve("release"), "release");
            await(first, "SUCCEEDED");
            await(second, "SUCCEEDED");
            await(third, "SUCCEEDED");
            assertTrue(Files.notExists(LocalModelServers.output.resolve("overlap")));
        } finally {
            Files.writeString(LocalModelServers.output.resolve("release"), "release");
        }
    }

    @Test
    @Order(6)
    void terminatesATimedOutProcessBeforeStartingTheNextJob() throws Exception {
        String id = submit("timeout-image", 512, 512, 1);
        await(id, "FAILED");
        long pid = Long.parseLong(lastCommand().getProperty("pid"));
        assertTrue(ProcessHandle.of(pid).isEmpty() || !ProcessHandle.of(pid).orElseThrow().isAlive());
        await(submit("Next image", 512, 512, 2), "SUCCEEDED");
    }

    private static Properties lastCommand() throws Exception {
        var properties = new Properties();
        try (var stream = Files.newInputStream(LocalModelServers.output.resolve("last-request.properties"))) {
            properties.load(stream);
        }
        return properties;
    }

    private static String submit(String prompt, int width, int height, long seed) {
        return given().contentType("application/json")
                .body(Map.of("prompt", prompt, "width", width, "height", height, "seed", seed))
                .post("/api/images").then().statusCode(202).extract().path("id");
    }

    private static void await(String id, String expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(8));
        String state;
        do {
            state = given().get("/api/jobs/" + id).then().statusCode(200).extract().path("state");
            if (!state.equals("QUEUED") && !state.equals("RUNNING")) {
                assertEquals(expected, state);
                return;
            }
            Thread.sleep(50);
        } while (Instant.now().isBefore(deadline));
        assertEquals(expected, state, "Job did not finish before the deadline");
    }
}
