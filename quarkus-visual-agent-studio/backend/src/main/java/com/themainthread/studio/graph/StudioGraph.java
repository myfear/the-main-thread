package com.themainthread.studio.graph;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record StudioGraph(int schemaVersion, UUID flowId, String name, String description,
        List<Node> nodes, List<Edge> edges, JsonNode metadata) {
    public record Node(String id, String type, int version, Position position, JsonNode config) {
    }

    public record Position(double x, double y) {
    }

    public record Edge(String id, String kind, String source, String sourcePort, String target, String targetPort) {
    }
}
