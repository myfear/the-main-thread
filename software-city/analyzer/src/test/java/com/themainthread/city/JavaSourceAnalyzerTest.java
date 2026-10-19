package com.themainthread.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class JavaSourceAnalyzerTest {
    private final JavaSourceAnalyzer parser = new JavaSourceAnalyzer();

    @Test
    void countsOnlyDirectExplicitMembersForEveryKind() throws Exception {
        String text = Files.readString(Path.of("../fixtures/source/app/src/main/java/example/Everything.java"));
        var result = parser.analyze(text, "app/src/main/java/example/Everything.java", "app", "head");
        assertTrue(result.diagnostics().isEmpty());
        Map<String, JavaSourceAnalyzer.ParsedType> types = result.types().stream()
                .collect(Collectors.toMap(JavaSourceAnalyzer.ParsedType::typeName, t -> t));
        assertEquals(6, types.size());
        assertMetrics(types.get("Everything"), "class", 2, 1, 3);
        assertMetrics(types.get("Action"), "interface", 2, 0, 1);
        assertMetrics(types.get("Point"), "record", 1, 2, 3);
        assertMetrics(types.get("State"), "enum", 1, 0, 1);
        assertMetrics(types.get("Label"), "annotation", 2, 0, 0);
        assertMetrics(types.get("Helper"), "class", 1, 0, 0);
        assertEquals(4, types.get("Everything").source().startLine());
        assertEquals(16, types.get("Everything").source().endLine());
        assertEquals(13, types.get("Everything").metrics().sourceLines());
    }

    @Test
    void supportsJava25ModuleImportsAndFlexibleConstructors() {
        var result = parser.analyze("""
                import module java.base;
                class Modern {
                    Modern(int n) {
                        if (n < 0) throw new IllegalArgumentException();
                        super();
                    }
                    String value(Object object) {
                        return switch (object) {
                            case String s -> s;
                            default -> "";
                        };
                    }
                }
                """, "Modern.java", ".", "head");
        assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
        assertMetrics(result.types().getFirst(), "class", 1, 1, 0);
    }

    @Test
    void compactSourcesAreSkippedButAnExplicitDollarNameIsNot() {
        var compact = parser.analyze("void main() {}", "Main.java", ".", "head");
        assertTrue(compact.types().isEmpty());
        assertEquals("COMPACT_SOURCE_SKIPPED", compact.diagnostics().getFirst().code());
        assertEquals("info", compact.diagnostics().getFirst().severity());
        assertEquals(1, parser.analyze("class $COMPACT_CLASS {}", "Named.java", ".", "head").types().size());
    }

    @Test
    void fingerprintsIgnoreImportsAndLocationButPreserveOriginalDeclarationText() {
        var first = type("package x;\nimport java.util.List;\n\tclass Café {\r\n\tvoid run() {}\r\n}");
        var moved = type("package x;\n\nimport java.util.Set;\n\tclass Café {\r\n\tvoid run() {}\r\n}");
        assertEquals(first.fingerprint(), moved.fingerprint());
        assertNotEquals(first.source().startLine(), moved.source().startLine());
        assertNotEquals(first.fingerprint(), type("package x;\nclass Café {\n\tvoid run() {}\n}").fingerprint());
        assertNotEquals(first.fingerprint(), type("package x;\nclass Café {\r\n\t/* change */void run() {}\r\n}").fingerprint());
    }

    @Test
    void malformedFilesDoNotLeakRecoveredDeclarations() {
        var result = parser.analyze("class Good {} class Bad { void x( }", "Bad.java", ".", "head");
        assertTrue(result.types().isEmpty());
        assertFalse(result.diagnostics().isEmpty());
        assertEquals("PARSE_ERROR", result.diagnostics().getFirst().code());
    }

    private JavaSourceAnalyzer.ParsedType type(String source) {
        var result = parser.analyze(source, "Example.java", ".", "head");
        assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
        return result.types().getFirst();
    }

    private static void assertMetrics(JavaSourceAnalyzer.ParsedType type, String kind, int methods,
            int constructors, int fields) {
        assertEquals(kind, type.kind());
        assertEquals(methods, type.metrics().methodCount(), type.typeName());
        assertEquals(constructors, type.metrics().constructorCount(), type.typeName());
        assertEquals(fields, type.metrics().fieldCount(), type.typeName());
    }
}
