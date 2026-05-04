package dev.topology.stream;

/**
 * One completed pipeline execution surfaced on the SSE feed.
 */
public record RunEvent(String requestSnippet, String summary) {}
