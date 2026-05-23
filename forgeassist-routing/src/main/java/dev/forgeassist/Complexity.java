package dev.forgeassist;

public enum Complexity {

    /**
     * Factual lookups, single-step how-tos, definitional questions.
     * Examples: "What does --dry-run do?", "List the ForgeCI environment
     * variables."
     */
    SIMPLE,

    /**
     * Multi-step reasoning, debugging with context, architectural trade-offs,
     * ambiguous or environment-specific problems.
     * Examples: "Why does my pipeline OOM only on cached arm64 builds?"
     */
    COMPLEX
}