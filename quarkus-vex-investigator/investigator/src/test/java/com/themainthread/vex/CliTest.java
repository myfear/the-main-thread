package com.themainthread.vex;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;

@QuarkusMainTest
public class CliTest {
    @Test @Launch("--help")
    public void showsWorkflow(LaunchResult result) {
        assertTrue(result.getOutput().contains("prepare"));
        assertTrue(result.getOutput().contains("approve"));
    }
}
