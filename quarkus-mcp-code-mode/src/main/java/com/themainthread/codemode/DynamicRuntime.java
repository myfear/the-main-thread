package com.themainthread.codemode;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.quickjs4j.ScriptInterfaceFactory;
import io.roastedroot.quickjs4j.core.GuestException;

final class DynamicRuntime {
    private DynamicRuntime() {
    }

    static JsonNode run(ScriptInterfaceFactory<DynamicScript, HostBridge> factory, String body,
            String inputJson, HostBridge bridge, int resultBytes) {
        DynamicScript script = factory.create(wrap(body), bridge);
        // The factory returns the interface; the generated implementation also implements AutoCloseable.
        try (var disposable = (AutoCloseable) script) {
            String value = script.run(inputJson);
            if (value == null) {
                throw new CodeModeFailure("INVALID_RESULT", "Script must return a JSON-compatible value.");
            }
            if (value.getBytes(StandardCharsets.UTF_8).length > resultBytes) {
                throw new CodeModeFailure("RESULT_LIMIT", "Returned JSON exceeds the permitted size.");
            }
            return Json.parse(value);
        } catch (CodeModeFailure e) {
            throw e;
        } catch (IllegalArgumentException e) {
            // The engine's original compile exception contains the entire script: never return it.
            if (e.getMessage() != null && e.getMessage().startsWith("Failed to compile JS code:")) {
                throw new CodeModeFailure("SYNTAX_ERROR", "JavaScript compilation failed; check the script syntax.");
            }
            throw new CodeModeFailure("HOST_ERROR", "Java host binding rejected the invocation.");
        } catch (GuestException e) {
            throw new CodeModeFailure("SCRIPT_ERROR", "JavaScript execution failed; check runtime errors and the return value.");
        } catch (Exception e) {
            throw new CodeModeFailure("HOST_ERROR", "Java host callback or script disposal failed.");
        }
    }

    static String wrap(String body) {
        return """
                export function run(inputJson) {
                    const input = JSON.parse(inputJson);
                    const callTool = (name, args) => JSON.parse(
                        DynamicScript_Builtins.callTool(name, JSON.stringify(args)));
                    const value = (function(input, callTool) {
                """ + body + """

                    })(input, callTool);
                    if (value && typeof value.then === 'function') {
                        throw new TypeError('This code tool requires a synchronous return value');
                    }
                    const json = JSON.stringify(value);
                    if (json === undefined) throw new TypeError('Return a JSON-compatible value');
                    return json;
                }
                """;
    }
}
