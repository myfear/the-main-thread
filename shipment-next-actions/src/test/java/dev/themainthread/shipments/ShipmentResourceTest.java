package dev.themainthread.shipments;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import dev.themainthread.shipments.service.ShipmentStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ShipmentResourceTest {

    @Inject
    ShipmentStore store;

    @BeforeEach
    void resetStore() {
        store.clear();
    }

    @Test
    void shouldReturnPlainJsonByDefault() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "trackingNumber":"TMT-2001",
                          "recipient":"Linus Torvalds",
                          "destinationCity":"Helsinki"
                        }
                        """)
                .when()
                .post("/shipments")
                .then()
                .statusCode(201)
                .body("status", is("CREATED"));

        given()
                .when()
                .get("/shipments")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].trackingNumber", is("TMT-2001"))
                .body("[0].status", is("CREATED"));
    }

    @Test
    void shouldAdvertiseOnlyValidNextActionsInHal() {
        int id = given()
                .contentType("application/json")
                .body("""
                        {
                          "trackingNumber":"TMT-2002",
                          "recipient":"Barbara Liskov",
                          "destinationCity":"Boston"
                        }
                        """)
                .when()
                .post("/shipments")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .accept("application/hal+json")
                .when()
                .get("/shipments/{id}", id)
                .then()
                .statusCode(200)
                .body("_links.self.href", endsWith("/shipments/" + id))
                .body("_links.list.href", endsWith("/shipments"))
                .body("_links.pay.href", endsWith("/shipments/" + id + "/pay"))
                .body("_links.cancel.href", endsWith("/shipments/" + id + "/cancel"))
                .body("_links.pack", nullValue());

        given()
                .when()
                .put("/shipments/{id}/pay", id)
                .then()
                .statusCode(200)
                .body("status", is("PAID"));

        given()
                .accept("application/hal+json")
                .when()
                .get("/shipments/{id}", id)
                .then()
                .statusCode(200)
                .body("_links.pack.href", endsWith("/shipments/" + id + "/pack"))
                .body("_links.cancel.href", endsWith("/shipments/" + id + "/cancel"))
                .body("_links.pay", nullValue())
                .body("_links.ship", nullValue());
    }

    @Test
    void shouldRejectInvalidTransitions() {
        int id = given()
                .contentType("application/json")
                .body("""
                        {
                          "trackingNumber":"TMT-2003",
                          "recipient":"Margaret Hamilton",
                          "destinationCity":"Cambridge"
                        }
                        """)
                .when()
                .post("/shipments")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .when()
                .put("/shipments/{id}/ship", id)
                .then()
                .statusCode(409)
                .body("error", is("transition_not_allowed"))
                .body("message", is("Shipment %d in status CREATED cannot ship".formatted(id)));
    }
}
