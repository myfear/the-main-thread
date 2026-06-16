package dev.windowwatch;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import dev.windowwatch.testsupport.StubAiProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(StubAiProfile.class)
class WindowWatchResourceTest {

    @Test
    void chatReturnsAnswerAndBudget() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"prompt\":\"Remember customer Orbital Freight.\"}")
                .when()
                .post("/api/chat/test-lane")
                .then()
                .statusCode(200)
                .body("answer", notNullValue())
                .body("budget.memoryId", equalTo("test-lane"))
                .body("budget.usedTokens", equalTo(20))
                .body("budget.maxTokens", equalTo(120))
                .body("budget.state", equalTo("ok"))
                .body("budget.retainedTurnTokens", equalTo(20))
                .body("budget.otherRetainedTokens", equalTo(0))
                .body("budget.turns.size()", equalTo(1))
                .body("budget.turns[0].userActiveInWindow", equalTo(true))
                .body("budget.turns[0].assistantActiveInWindow", equalTo(true));
    }

    @Test
    void budgetEndpointReturnsSnapshot() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"prompt\":\"First turn.\"}")
                .when()
                .post("/api/chat/test-lane-2")
                .then()
                .statusCode(200);

        given()
                .when()
                .get("/api/budget/test-lane-2")
                .then()
                .statusCode(200)
                .body("memoryId", equalTo("test-lane-2"))
                .body("turns.size()", equalTo(1));
    }
}
