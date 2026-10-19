package com.themainthread.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@QuarkusMainTest
public class CommandTest {
    @TempDir
    Path temp;

    @Test
    void analyzesAndValidatesJsonUsingTheActualCommand(QuarkusMainLauncher launcher) throws Exception {
        try (var repository = new TestRepository(temp.resolve("repository"))) {
            repository.copyFixture();
            repository.commit();
            Path output = temp.resolve("city.json");
            var result = launcher.launch("analyze", "--repo", repository.root.toString(),
                    "--ref", "HEAD", "--strict", "--out", output.toString());
            assertEquals(0, result.exitCode(), result.getOutput());
            assertTrue(result.getOutput().contains("Exported 9 types"));
            var mapper = new ObjectMapper();
            var document = mapper.readValue(Files.readAllBytes(output), CityDocument.class);
            new CityJson(mapper).validate(document);
            assertEquals(9, document.summary().typeCountHead());
            byte[] first = Files.readAllBytes(output);
            var again = launcher.launch("analyze", "--repo", repository.root.toString(), "--out", output.toString());
            assertEquals(0, again.exitCode());
            assertArrayEquals(first, Files.readAllBytes(output));
        }
    }

    @Test
    void strictFailurePreservesOutputAndPermissiveExportCarriesDiagnostics(QuarkusMainLauncher launcher)
            throws Exception {
        try (var repository = new TestRepository(temp.resolve("repository"))) {
            repository.write("src/main/java/Bad.java", "class Bad { void x( }");
            repository.commit();
            Path output = temp.resolve("city.json");
            Files.writeString(output, "existing export");
            var strict = launcher.launch("analyze", "--repo", repository.root.toString(),
                    "--strict", "--out", output.toString());
            assertEquals(1, strict.exitCode());
            assertEquals("existing export", Files.readString(output));
            var permissive = launcher.launch("analyze", "--repo", repository.root.toString(), "--out", output.toString());
            assertEquals(0, permissive.exitCode());
            var node = new ObjectMapper().readTree(output.toFile());
            assertTrue(node.path("summary").path("incomplete").asBoolean());
            assertEquals("PARSE_ERROR", node.path("diagnostics").get(0).path("code").asText());
        }
    }

    @Test
    void invalidArgumentsMissingRefsAndProtectedOutputsHaveDistinctExitCodes(QuarkusMainLauncher launcher)
            throws Exception {
        try (var repository = new TestRepository(temp.resolve("repository"))) {
            repository.write("src/main/java/A.java", "class A {}");
            repository.commit();
            Path output = temp.resolve("city.json");
            assertEquals(2, launcher.launch("analyze").exitCode());
            assertEquals(2, launcher.launch("analyze", "--repo", repository.root.toString(),
                    "--out", output.toString(), "--include", "../*.java").exitCode());
            assertEquals(1, launcher.launch("analyze", "--repo", repository.root.toString(),
                    "--out", output.toString(), "--ref", "not-local").exitCode());
            Path protectedOutput = repository.root.resolve("city.json");
            assertEquals(1, launcher.launch("analyze", "--repo", repository.root.toString(),
                    "--out", protectedOutput.toString()).exitCode());
            assertFalse(Files.exists(protectedOutput));
        }
    }
}
