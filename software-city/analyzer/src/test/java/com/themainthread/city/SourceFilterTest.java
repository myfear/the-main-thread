package com.themainthread.city;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SourceFilterTest {
    @Test
    void defaultMatchesRootAndNestedPackagesButNotTestOrGeneratedSources() {
        var filter = new SourceFilter(List.of(), List.of());
        assertTrue(filter.accepts("src/main/java/Root.java"));
        assertTrue(filter.accepts("app/src/main/java/a/b/C.java"));
        assertFalse(filter.accepts("src/test/java/Test.java"));
        assertFalse(filter.accepts("target/app/src/main/java/C.java"));
        assertFalse(filter.accepts("app/src/main/java/generated/C.java"));
    }

    @Test
    void includeReplacesDefaultAndExclusionsWin() {
        var filter = new SourceFilter(List.of("**/src/test/java/?ase.java"), List.of("**/Skip*/**"));
        assertTrue(filter.accepts("src/test/java/Case.java"));
        assertFalse(filter.accepts("src/main/java/Case.java"));
        assertFalse(filter.accepts("SkipApp/src/test/java/Case.java"));
        assertFalse(filter.accepts("src/test/java/case.JAVA"));
    }

    @Test
    void rejectsUnsupportedAndUnsafePatterns() {
        for (String pattern : List.of("", "/absolute", "../*.java", "C:\\*.java", "**x", "**/[A].java")) {
            assertThrows(IllegalArgumentException.class, () -> new SourceFilter(List.of(pattern), List.of()));
        }
    }
}
