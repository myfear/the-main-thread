package com.themainthread.shipment;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;

@QuarkusTest
class ShipmentGraphQLTest {

    @Test
    void shouldUseTheDefaultWarehouseWhenNoHeaderIsPresent() {
        given()
                .contentType(ContentType.JSON)
                .body(graphql("""
                        query {
                          viewerWarehouse
                          shipments {
                            id
                            warehouseCode
                          }
                        }
                        """))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.viewerWarehouse", equalTo("BER"))
                .body("data.shipments.id", hasItems("BER-1001", "BER-1002"))
                .body("data.shipments.warehouseCode", hasItems("BER"));
    }

    @Test
    void shouldFilterShipmentsByWarehouseHeader() {
        given()
                .header("X-Warehouse-Code", "AMS")
                .contentType(ContentType.JSON)
                .body(graphql("""
                        query {
                          viewerWarehouse
                          shipments {
                            id
                            warehouseCode
                            status
                          }
                        }
                        """))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.viewerWarehouse", equalTo("AMS"))
                .body("data.shipments.size()", equalTo(1))
                .body("data.shipments[0].id", equalTo("AMS-2001"))
                .body("data.shipments[0].status", equalTo("PICKED"));
    }

    @Test
    void shouldUpdateStatusThroughTheMutation() {
        given()
                .header("X-Warehouse-Code", "BER")
                .contentType(ContentType.JSON)
                .body(graphql("""
                        mutation {
                          updateShipmentStatus(id: "BER-1001", status: DELIVERED) {
                            id
                            status
                          }
                        }
                        """))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.updateShipmentStatus.id", equalTo("BER-1001"))
                .body("data.updateShipmentStatus.status", equalTo("DELIVERED"));

        given()
                .contentType(ContentType.JSON)
                .body(graphql("""
                        query {
                          shipment(id: "BER-1001") {
                            id
                            status
                          }
                        }
                        """))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.shipment.status", equalTo("DELIVERED"));
    }

    @Test
    void shouldExposeGraphiqlThroughTheExtension() {
        given()
                .when()
                .get("/q/graphql-ui")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    private Map<String, String> graphql(String query) {
        return Map.of("query", query);
    }
}
