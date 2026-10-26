package com.themainthread.mavenlab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunRecordTest {
    @TempDir
    Path temporary;

    @Test
    void partialRunDoesNotRelabelOrInheritAnEarlierRun() throws Exception {
        RunRecord full = record(RunRecord.SCENARIOS, "25.0.1");
        passRequested(full, RunRecord.SCENARIOS);
        String before = Files.readString(full.directory().resolve("summary.json"));

        RunRecord partial = record(List.of("validation"), "25.0.2");
        passRequested(partial, List.of("validation"));
        String after = Files.readString(full.directory().resolve("summary.json"));
        assertEquals(before, after);
        assertNotEquals(full.id(), partial.id());
        assertEquals("partial", partial.document().get("scope"));
        assertEquals(Map.of("java_version", "25.0.2"), partial.document().get("runtime"));
        assertEquals(List.of("validation"), partial.document().get("requested_scenarios"));
        assertEquals(1, ((Map<?, ?>) partial.document().get("scenarios")).size());
        assertFalse(Files.readString(partial.directory().resolve("summary.json")).contains("25.0.1"));
    }

    @Test
    void successfulPartialRunCannotBeSnapshotted() throws Exception {
        RunRecord partial = record(List.of("validation"), "25");
        passRequested(partial, List.of("validation"));
        assertThrows(IOException.class, () -> RunRecord.snapshot(partial.directory(), temporary.resolve("snapshot")));
        assertFalse(Files.exists(temporary.resolve("snapshot")));
    }

    @Test
    void failedRunRetainsFailureAndCannotBeSnapshotted() throws Exception {
        RunRecord failed = record(RunRecord.SCENARIOS, "25");
        failed.start("validation");
        failed.fail("validation", new IOException("expected test failure"));
        assertEquals("failed", failed.document().get("status"));
        assertTrue(Files.readString(failed.directory().resolve("summary.json")).contains("expected test failure"));
        assertFalse(Files.exists(failed.directory().resolve("completed.properties")));
        assertThrows(IOException.class, () -> RunRecord.snapshot(failed.directory(), temporary.resolve("snapshot")));
        assertThrows(IllegalStateException.class, failed::finish);
    }

    @Test
    void interruptedOrIncompleteRunCannotClaimSuccess() throws Exception {
        RunRecord running = record(RunRecord.SCENARIOS, "25");
        running.start("validation");
        assertThrows(IllegalStateException.class, running::finish);
        assertEquals("running", running.document().get("status"));
        assertThrows(IOException.class, () -> RunRecord.snapshot(running.directory(), temporary.resolve("snapshot")));
    }

    @Test
    void fullSnapshotCopiesOnlyFilesSealedByThatRunAndNeverOverwrites() throws Exception {
        RunRecord full = record(RunRecord.SCENARIOS, "25");
        Files.writeString(full.directory().resolve("example.log"), "a log\n");
        passRequested(full, RunRecord.SCENARIOS);
        Files.writeString(full.directory().resolve("unrelated.txt"), "added after completion\n");
        Path snapshot = temporary.resolve("snapshot");
        RunRecord.snapshot(full.directory(), snapshot);
        assertEquals("a log\n", Files.readString(snapshot.resolve("example.log")));
        assertEquals(Files.readString(full.directory().resolve("summary.json")),
                Files.readString(snapshot.resolve("summary.json")));
        assertFalse(Files.exists(snapshot.resolve("unrelated.txt")));
        assertThrows(IOException.class, () -> RunRecord.snapshot(full.directory(), snapshot));
        assertThrows(IllegalStateException.class, () -> full.start("validation"));
    }

    @Test
    void changedSummaryOrLogInvalidatesSnapshot() throws Exception {
        for (String filename : List.of("summary.json", "example.log")) {
            RunRecord full = record(RunRecord.SCENARIOS, "25");
            Files.writeString(full.directory().resolve("example.log"), "original\n");
            passRequested(full, RunRecord.SCENARIOS);
            Files.writeString(full.directory().resolve(filename), "changed\n");
            Path destination = temporary.resolve("snapshot-" + filename);
            assertThrows(IOException.class, () -> RunRecord.snapshot(full.directory(), destination));
            assertFalse(Files.exists(destination));
        }
    }

    @Test
    void aScopeLabelAloneCannotPromotePartialEvidenceToFull() throws Exception {
        RunRecord partial = record(List.of("validation"), "25");
        passRequested(partial, List.of("validation"));
        Path manifest = partial.directory().resolve("completed.properties");
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(manifest)) {
            properties.load(reader);
        }
        properties.setProperty("run.scope", "full");
        properties.setProperty("run.scenarios", String.join(",", RunRecord.SCENARIOS));
        try (var writer = Files.newBufferedWriter(manifest)) {
            properties.store(writer, "Simulate mislabeled partial evidence");
        }
        assertThrows(IOException.class, () -> RunRecord.snapshot(partial.directory(), temporary.resolve("snapshot")));
    }

    private RunRecord record(List<String> scenarios, String javaVersion) throws IOException {
        return new RunRecord(temporary.resolve("runs"), scenarios,
                Map.of("java_version", javaVersion), Map.of("3.10.0", "checksum"), "runner-hash");
    }

    private static void passRequested(RunRecord record, List<String> scenarios) throws IOException {
        for (String name : scenarios) {
            record.start(name);
            record.pass(name, Map.of("example_result", true));
        }
        record.finish();
    }
}
