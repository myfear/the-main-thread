package org.mainthread.rcac;

import static io.restassured.RestAssured.given;
import static io.restassured.config.LogConfig.logConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.restassured.specification.RequestSpecification;

abstract class CustomerHttpContract {
    static RequestSpecification as(String user) {
        String key = switch (user) {
            case "alice" -> "HTTP_ALICE_PASSWORD";
            case "bob" -> "HTTP_BOB_PASSWORD";
            case "auditor" -> "HTTP_AUDITOR_PASSWORD";
            case "unmapped" -> "HTTP_UNMAPPED_PASSWORD";
            default -> throw new IllegalArgumentException("Unknown test user");
        };
        return given().config(config().logConfig(logConfig().blacklistHeader("Authorization")))
                .auth().preemptive().basic(user, Db2Support.requiredEnvironment(key));
    }

    @Test
    void authenticatesBothEndpointsAndRejectsUnmappedPrincipals() {
        for (String path : List.of("/customers", "/customers/101")) {
            given().get(path).then().statusCode(401);
            given().auth().preemptive().basic("alice", "incorrect-password").get(path).then().statusCode(401);
            as("unmapped").get(path).then().statusCode(403);
        }
    }

    @Test
    void returnsDatabaseFilteredRowsAndDatabaseMaskedFields() throws Exception {
        for (String user : List.of("alice", "bob", "auditor")) {
            PoolIdentity identity = PoolIdentity.forPrincipal(user).orElseThrow();
            Customer[] customers = as(user).get("/customers").then().statusCode(200)
                    .extract().as(Customer[].class);
            Db2Support.assertCustomers(Arrays.asList(customers), identity);
            Customer customer = as(user).get("/customers/" + customers[0].id()).then().statusCode(200)
                    .extract().as(Customer.class);
            assertEquals(customers[0], customer);
        }
        Db2Support.evidence(getClass().getSimpleName() + "-http.json",
                Map.of("aliceIds", List.of(101, 102, 103), "bobIds", List.of(201, 202, 203),
                        "aliceSensitiveFields", "NULL", "auditorSensitiveFields", "visible within A"));
    }

    @Test
    void foreignAndNonexistentIdsHaveTheSame404Body() {
        for (String user : List.of("alice", "bob", "auditor")) {
            long foreign = user.equals("bob") ? 101 : 201;
            String hidden = as(user).get("/customers/" + foreign).then().statusCode(404)
                    .body("message", equalTo("Customer not found")).extract().asString();
            String absent = as(user).get("/customers/999999").then().statusCode(404).extract().asString();
            assertEquals(hidden, absent);
        }
    }

    @Test
    void callerSuppliedTenantAndRoleValuesCannotSelectAnotherPool() {
        Customer[] customers = as("alice").queryParam("tenant", "B").queryParam("datasource", "tenant-b")
                .queryParam("role", "RCAC_PII").header("X-Tenant", "B").header("X-Role", "RCAC_PII")
                .get("/customers").then().statusCode(200).extract().as(Customer[].class);
        Db2Support.assertCustomers(Arrays.asList(customers), PoolIdentity.TENANT_A);
    }

    @Test
    void concurrentHttpRequestsRetainTheirOwnVisibility() throws Exception {
        List<Callable<Boolean>> work = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            for (String user : List.of("alice", "bob", "auditor")) {
                work.add(() -> {
                    Customer[] customers = as(user).get("/customers").then().statusCode(200)
                            .extract().as(Customer[].class);
                    Db2Support.assertCustomers(Arrays.asList(customers), PoolIdentity.forPrincipal(user).orElseThrow());
                    return true;
                });
            }
        }
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var result : executor.invokeAll(work, 60, TimeUnit.SECONDS)) {
                assertTrue(!result.isCancelled());
                assertTrue(result.get());
            }
        }
    }
}
