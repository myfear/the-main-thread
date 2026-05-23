package dev.forgeassist;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ForgeAssistResourceTest {

    @InjectMock
    ModelRouter router;

    @Test
    void assistEndpointDelegatesToRouter() {
        when(router.route("What does --no-cache do?")).thenReturn("fast-lane");

        given()
                .contentType("text/plain")
                .body("What does --no-cache do?")
                .when()
                .post("/assist")
                .then()
                .statusCode(200)
                .body(is("fast-lane"));
    }
}