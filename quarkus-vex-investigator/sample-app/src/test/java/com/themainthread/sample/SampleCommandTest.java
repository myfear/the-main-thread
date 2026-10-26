package com.themainthread.sample;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;

@QuarkusMainTest
class SampleCommandTest {
    @Test @Launch({})
    void fixtureDoesNotInterpolate(LaunchResult result) {
        assertTrue(result.getOutput().contains("No interpolation is performed"));
    }
}
