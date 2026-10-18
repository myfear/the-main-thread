package com.themainthread.codemode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.quickjs4j.ScriptInterfaceFactory;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class FactoryTest {
    @Inject
    ScriptInterfaceFactory<DynamicScript, HostBridge> factory;

    @Test
    void generatedCdiFactoryAcceptsDynamicModuleAndCallsJava() throws Exception {
        var script = factory.create("""
                export function run(inputJson) {
                    return DynamicScript_Builtins.callTool("echo", inputJson);
                }
                """, new HostBridge((tool, json) -> json));
        try (var disposable = assertInstanceOf(AutoCloseable.class, script)) {
            assertEquals("{\"message\":\"Grüße 🌍\"}", script.run("{\"message\":\"Grüße 🌍\"}"));
        }
    }

    @Test
    void thrownJavaCallbackIsSanitizedAndFactoryRemainsUsable() {
        var bridge = new HostBridge((tool, json) -> {
            throw new IllegalStateException("private-token-never-return-this " + "x".repeat(10000));
        });
        var failure = assertThrows(CodeModeFailure.class, () -> DynamicRuntime.run(factory,
                "return callTool('echo', {});", "{}", bridge, 16384));
        assertEquals("HOST_ERROR", failure.kind);
        assertFalse(failure.getMessage().contains("private-token"));
        assertEquals(42, DynamicRuntime.run(factory, "return 42;", "{}", bridge, 16384).asInt());
    }

    @Test
    void existingFileCannotBeImportedAsAnArbitraryModule() throws Exception {
        var module = java.nio.file.Files.createTempFile("quickjs-forbidden-", ".mjs");
        java.nio.file.Files.writeString(module, "export const secret = 'must-not-be-loaded';");
        try {
            for (String specifier : new String[] {module.toString(), module.toUri().toString()}) {
                // Valid module syntax, supplied directly to the factory: no wrapper or Promise rejection.
                var script = factory.create("import {secret} from " + Json.MAPPER.writeValueAsString(specifier)
                        + "; export function run(inputJson) { return secret; }", new HostBridge((tool, json) -> json));
                try (var disposable = (AutoCloseable) script) {
                    assertThrows(RuntimeException.class, () -> script.run("{}"));
                }
            }
        } finally {
            java.nio.file.Files.delete(module);
        }
    }
}
