package com.mainthread.lambdahttp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class LambdaEventServerTest {

    @Test
    void shouldAcceptRawApiGatewayEvent() {
        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setVersion("2.0");
        event.setRouteKey("$default");
        event.setRawPath("/quotes/porto");
        event.setRawQueryString("speed=overnight&weightGrams=900");
        event.setHeaders(Map.of("X-Customer-Tier", "gold"));

        APIGatewayV2HTTPEvent.RequestContext.Http http = new APIGatewayV2HTTPEvent.RequestContext.Http();
        http.setMethod("GET");
        http.setPath("/quotes/porto");
        http.setProtocol("HTTP/1.1");
        http.setSourceIp("127.0.0.1");
        http.setUserAgent("rest-assured");

        APIGatewayV2HTTPEvent.RequestContext requestContext = new APIGatewayV2HTTPEvent.RequestContext();
        requestContext.setHttp(http);
        requestContext.setRequestId("req-raw-42");
        requestContext.setRouteKey("$default");
        requestContext.setStage("qa");
        requestContext.setApiId("local-test");
        requestContext.setDomainName("localhost");

        event.setRequestContext(requestContext);

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(event)
                .when()
                .post("/_lambda_")
                .then()
                .statusCode(200)
                .body("statusCode", equalTo(200))
                .body("body", containsString("\"gatewayRequestId\":\"req-raw-42\""))
                .body("body", containsString("\"stage\":\"qa\""))
                .body("body", containsString("\"quotedCents\":1540"));
    }
}
