package io.mainthread.catalogboard;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class ProductResourceTest {

    @Test
    void createsAndReadsProduct() {
        String sku = "SKU-" + System.nanoTime();

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "sku": "%s",
                          "name": "Field Notebook",
                          "category": "stationery",
                          "stock": 8,
                          "reorderPoint": 3
                        }
                        """.formatted(sku))
                .when()
                .post("/products")
                .then()
                .statusCode(201)
                .body("sku", equalTo(sku))
                .body("needsRestock", equalTo(false));

        given()
                .when()
                .get("/products/{sku}", sku)
                .then()
                .statusCode(200)
                .body("name", equalTo("Field Notebook"))
                .body("category", equalTo("stationery"));
    }

    @Test
    void listsProductsByCategoryWithPagination() {
        String category = "category-" + System.nanoTime();
        createProduct("SKU-A-" + System.nanoTime(), "Alpha Binder", category, 4, 2);
        createProduct("SKU-B-" + System.nanoTime(), "Beta Binder", category, 4, 2);

        given()
                .queryParam("category", category)
                .queryParam("page", 0)
                .queryParam("size", 1)
                .when()
                .get("/products")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].name", equalTo("Alpha Binder"));
    }

    @Test
    void changesStockThroughStatelessRepositoryMethod() {
        String sku = "SKU-STOCK-" + System.nanoTime();
        createProduct(sku, "Shelf Label", "warehouse", 2, 5);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "delta": 4
                        }
                        """)
                .when()
                .patch("/products/{sku}/stock", sku)
                .then()
                .statusCode(200)
                .body("stock", equalTo(6))
                .body("needsRestock", equalTo(false));
    }

    @Test
    void findsLowStockProducts() {
        String sku = "SKU-LOW-" + System.nanoTime();
        createProduct(sku, "Packing Tape", "warehouse", 1, 5);

        given()
                .when()
                .get("/products/low-stock")
                .then()
                .statusCode(200)
                .body("sku", hasItem(sku))
                .body("findAll { it.sku == '%s' }.size()".formatted(sku), greaterThanOrEqualTo(1));
    }

    @Test
    void hidesDiscontinuedProductsFromCatalog() {
        String sku = "SKU-DISC-" + System.nanoTime();
        createProduct(sku, "Legacy Marker", "stationery", 4, 2);

        given()
                .when()
                .delete("/products/{sku}", sku)
                .then()
                .statusCode(204);

        given()
                .when()
                .get("/products/{sku}", sku)
                .then()
                .statusCode(404);

        given()
                .when()
                .get("/products/search?q=Legacy")
                .then()
                .statusCode(200)
                .body("sku", not(hasItem(sku)));
    }

    @Test
    void rejectsInvalidCreateRequest() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "sku": "",
                          "name": "",
                          "category": "stationery",
                          "stock": -1,
                          "reorderPoint": 0
                        }
                        """)
                .when()
                .post("/products")
                .then()
                .statusCode(400);
    }

    private void createProduct(String sku, String name, String category, int stock, int reorderPoint) {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "sku": "%s",
                          "name": "%s",
                          "category": "%s",
                          "stock": %d,
                          "reorderPoint": %d
                        }
                        """.formatted(sku, name, category, stock, reorderPoint))
                .when()
                .post("/products")
                .then()
                .statusCode(201);
    }
}
