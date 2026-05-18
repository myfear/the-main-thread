package com.catalogapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

final class JsonContractAssertions {

    private JsonContractAssertions() {
    }

    static void assertSummariesContract() {
        given().when()
                .get("/products/summaries")
                .then()
                .statusCode(200)
                .body("$", hasSize(4))
                .body("sku", hasItem("SKU-001"))
                .body("find { it.sku == 'SKU-001' }.price.currency", equalTo("USD"))
                .body("find { it.sku == 'SKU-001' }.price.amountMinor", equalTo(12999))
                .body("find { it.sku == 'SKU-001' }.price.display", equalTo("USD 129.99"));
    }

    static void assertPageContract() {
        given().when()
                .get("/products/page")
                .then()
                .statusCode(200)
                .body("total", equalTo(4))
                .body("items", hasSize(4))
                .body("items[0].id", is(1))
                .body("items[0].price.display", equalTo("USD 129.99"));
    }

    static void assertPolymorphicContract() {
        given().when()
                .get("/catalog/payloads/demo")
                .then()
                .statusCode(200)
                .body("$", hasSize(3))
                .body("[0].type", equalTo("product"))
                .body("[0].sku", equalTo("SKU-001"))
                .body("[2].type", equalTo("bundle"))
                .body("[2].name", equalTo("Desk Bundle"))
                .body("[2].skuList", hasSize(2))
                .body("[2].totalPrice.display", equalTo("USD 529.98"));
    }
}
