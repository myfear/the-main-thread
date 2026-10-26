package com.themainthread.vex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainIntegrationTest;

@QuarkusMainIntegrationTest
public class CliIT extends CliTest {
    @TempDir Path temporary;

    @Test
    public void packagedPipelineUsesBundledSchemasAndRefusesSilentApproval(QuarkusMainLauncher launcher) throws Exception {
        Path root = temporary.resolve("TEST_ONLY-packaged");
        var result = launcher.launch("test-demo", "--project", "../sample-app", "--out", root.toString());
        assertEquals(0, result.exitCode(), result.getErrorOutput());
        assertTrue(Files.readString(root.resolve("vex.json")).contains("TEST_ONLY"));
        SchemaChecks.validate("openvex-v0.2.0.schema.json", EvidenceHasher.read(root.resolve("vex.json")));
        assertEquals(5, launcher.launch("approve", "--case", root.toString()).exitCode());
    }
}
