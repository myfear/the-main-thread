package com.themainthread.noir;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class LibrarySearchTest {

    @Test
    void snowmanExistsInTheDatabase() {
        given()
                .when().get("/library/books?title=The Snowman")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("bookId", equalTo(1))
                .body("title", equalTo("The Snowman"))
                .body("authorLastName", equalTo("Nesbø"));
    }

    @Test
    void nesboAuthorDocumentIsIndexed() {
        given()
                .when().get("/library/index/author/1")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("found", equalTo(true))
                .body("_source.lastName", equalTo("Nesbø"))
                .body("_source.books.title", hasItem("The Snowman"));
    }

    @Test
    void nameAnalyzerFoldsNesboToAscii() {
        given()
                .when().get("/library/analyze?analyzer=name&text=Nesbø")
                .then()
                .statusCode(200)
                .body("tokens", hasItem("nesbo"));

        given()
                .when().get("/library/analyze?analyzer=name&text=Nesbo")
                .then()
                .statusCode(200)
                .body("tokens", hasItem("nesbo"));
    }

    @Test
    void asciiSearchFindsJoNesbo() {
        given()
                .when().get("/library/search?q=Nesbo")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("lastName", hasItems("Nesbø"))
                .body("find { it.lastName == 'Nesbø' }.books.title", hasItem("The Snowman"));
    }
}
