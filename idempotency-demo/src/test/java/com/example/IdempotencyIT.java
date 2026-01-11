package com.example;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
public class IdempotencyIT extends IdempotencyTest {
    // Execute the same tests but in packaged mode.
}
