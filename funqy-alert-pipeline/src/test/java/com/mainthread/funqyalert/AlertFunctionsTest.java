package com.mainthread.funqyalert;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AlertFunctionsTest {

    @Test
    void previewAlertSupportsGetQueryParameters() {
        given()
                .queryParam("service", "payments")
                .queryParam("environment", "prod")
                .queryParam("region", "us-east-1")
                .queryParam("summary", "Checkout timeouts spreading")
                .queryParam("errorRatePercent", 7.2)
                .queryParam("impactedCustomers", 1800)
                .queryParam("acknowledged", false)
                .when()
                .get("/previewAlert")
                .then()
                .statusCode(200)
                .body("severity", equalTo("critical"))
                .body("riskScore", equalTo(100))
                .body("destinationTeam", equalTo("payments-oncall"))
                .body("pageImmediately", equalTo(true))
                .body("acknowledgeWithinMinutes", equalTo(5))
                .body("triggeringEventSource", equalTo("localhost"));
    }

    @Test
    void ingestAlertAcceptsJson() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "service": "Search",
                          "environment": "staging",
                          "region": "eu-west-1",
                          "summary": "Search latency climbing",
                          "errorRatePercent": 2.4,
                          "impactedCustomers": 420,
                          "acknowledged": false
                        }
                        """)
                .when()
                .post("/ingestAlert")
                .then()
                .statusCode(200)
                .body("service", equalTo("search"))
                .body("severity", equalTo("high"))
                .body("dedupeKey", equalTo("search:eu-west-1:search-latency-climbing"))
                .body("checkpoints", hasItems("validated", "ingested"));
    }

    @Test
    void scoreAlertCanBeTriggeredByBinaryCloudEvent() {
        given()
                .contentType(ContentType.JSON)
                .header("Ce-Id", "binary-score-1")
                .header("Ce-Specversion", "1.0")
                .header("Ce-Type", "ingestAlert.output")
                .header("Ce-Source", "urn:test:binary")
                .body("""
                        {
                          "service": "catalog",
                          "environment": "prod",
                          "region": "us-west-2",
                          "summary": "catalog misses rising",
                          "errorRatePercent": 3.2,
                          "impactedCustomers": 640,
                          "acknowledged": false,
                          "severity": "high",
                          "dedupeKey": "catalog:us-west-2:catalog-misses-rising",
                          "checkpoints": ["validated", "ingested"]
                        }
                        """)
                .when()
                .post("/")
                .then()
                .statusCode(200)
                .body("riskScore", equalTo(86))
                .body("checkpoints", hasItems("validated", "ingested", "scored"));
    }

    @Test
    void routeAlertCanBeTriggeredByStructuredCloudEvent() {
        given()
                .contentType("application/cloudevents+json")
                .body("""
                        {
                          "specversion": "1.0",
                          "id": "structured-route-1",
                          "source": "urn:test:structured",
                          "type": "com.mainthread.alert.scored",
                          "datacontenttype": "application/json",
                          "data": {
                            "service": "checkout",
                            "environment": "prod",
                            "region": "us-east-1",
                            "summary": "checkout retries spiraling",
                            "errorRatePercent": 6.8,
                            "impactedCustomers": 900,
                            "acknowledged": false,
                            "severity": "critical",
                            "riskScore": 100,
                            "dedupeKey": "checkout:us-east-1:checkout-retries-spiraling",
                            "checkpoints": ["validated", "ingested", "scored"]
                          }
                        }
                        """)
                .when()
                .post("/")
                .then()
                .statusCode(200)
                .body("type", equalTo("com.mainthread.alert.routed"))
                .body("source", equalTo("routeAlert"))
                .body("data.destinationTeam", equalTo("checkout-oncall"))
                .body("data.triggeringEventId", equalTo("structured-route-1"))
                .body("data.triggeringEventSource", equalTo("urn:test:structured"))
                .body("data.checkpoints", hasItems("validated", "ingested", "scored", "routed"));
    }
}
