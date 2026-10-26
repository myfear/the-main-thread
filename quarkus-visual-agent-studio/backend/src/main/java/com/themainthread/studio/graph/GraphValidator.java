package com.themainthread.studio.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import com.themainthread.studio.graph.StudioGraph.Edge;
import com.themainthread.studio.graph.StudioGraph.Node;

@ApplicationScoped
public class GraphValidator {
    public static final int MAX_NODES = 32;
    public static final int MAX_EDGES = 64;
    private final NodeCatalog catalog;

    public GraphValidator(NodeCatalog catalog) {
        this.catalog = catalog;
    }

    public Validation validate(StudioGraph graph) {
        var errors = new ArrayList<Diagnostic>();
        if (graph == null || graph.nodes() == null || graph.edges() == null) {
            error(errors, "INVALID_GRAPH", null, null, "/", "Provide a graph with nodes and edges.");
            return new Validation(false, errors);
        }
        if (graph.schemaVersion() != 1 || graph.flowId() == null || graph.name() == null || graph.name().isBlank()
                || graph.name().length() > 120 || (graph.description() != null && graph.description().length() > 1000)) {
            error(errors, "INVALID_GRAPH_HEADER", null, null, "/", "Use schema version 1, a flow UUID, and a bounded name.");
        }
        if (graph.nodes().isEmpty() || graph.nodes().size() > MAX_NODES || graph.edges().size() > MAX_EDGES) {
            error(errors, "GRAPH_LIMIT", null, null, "/", "Use 1–32 nodes and at most 64 edges.");
            return new Validation(false, errors);
        }
        var nodes = new LinkedHashMap<String, Node>();
        for (int i = 0; i < graph.nodes().size(); i++) {
            Node node = graph.nodes().get(i);
            String path = "/nodes/" + i;
            if (node == null || node.id() == null || !node.id().matches("[A-Za-z][A-Za-z0-9_-]{0,39}")) {
                error(errors, "INVALID_NODE_ID", node == null ? null : node.id(), null, path,
                        "Node IDs must be 1–40 safe identifier characters.");
                continue;
            }
            if (nodes.putIfAbsent(node.id(), node) != null) {
                error(errors, "DUPLICATE_NODE_ID", node.id(), null, path, "Node IDs must be unique.");
                continue;
            }
            var descriptor = catalog.get(node.type());
            if (descriptor == null || node.version() != descriptor.version()) {
                error(errors, "UNSUPPORTED_NODE", node.id(), null, path, "This node type/version is not supported.");
                continue;
            }
            if (node.position() == null || !Double.isFinite(node.position().x()) || !Double.isFinite(node.position().y())) {
                error(errors, "INVALID_POSITION", node.id(), null, path + "/position", "Provide finite canvas coordinates.");
            }
            if (!catalog.validConfig(descriptor, node.config())) {
                error(errors, "INVALID_CONFIG", node.id(), null, path + "/config",
                        "Configuration must match the catalog schema and registered capability references.");
            }
        }
        var validEdges = new ArrayList<Edge>();
        var ids = new HashSet<String>();
        for (int i = 0; i < graph.edges().size(); i++) {
            Edge edge = graph.edges().get(i);
            String path = "/edges/" + i;
            if (edge == null || edge.id() == null || edge.id().isBlank() || edge.id().length() > 80) {
                error(errors, "INVALID_EDGE_ID", null, null, path, "Provide a bounded edge ID.");
                continue;
            }
            if (!ids.add(edge.id())) {
                error(errors, "DUPLICATE_EDGE_ID", null, edge.id(), path, "Edge IDs must be unique.");
            }
            Node source = nodes.get(edge.source());
            Node target = nodes.get(edge.target());
            if (source == null || target == null) {
                error(errors, "MISSING_ENDPOINT", edge.target(), edge.id(), path, "An edge endpoint does not exist.");
                continue;
            }
            var from = catalog.get(source.type());
            var to = catalog.get(target.type());
            if (from == null || to == null) {
                continue;
            }
            if (edge.sourcePort() == null || edge.sourcePort().isBlank()
                    || edge.targetPort() == null || edge.targetPort().isBlank()) {
                error(errors, "INVALID_PORT_CONNECTION", target.id(), edge.id(), path,
                        "Both named ports are required.");
                continue;
            }
            boolean valid = switch (edge.kind() == null ? "" : edge.kind()) {
                case "control" -> from.controlOutputs().contains(edge.sourcePort())
                        && to.controlInputs().contains(edge.targetPort());
                case "data" -> compatiblePorts(from, to, edge);
                case "capability" -> from.capabilityOutput() != null
                        && from.capabilityOutput().equals(edge.sourcePort())
                        && to.capabilityInputs().containsKey(edge.targetPort())
                        && source.type().equals(to.capabilityInputs().get(edge.targetPort()).type());
                default -> false;
            };
            if (!valid) {
                error(errors, "INVALID_PORT_CONNECTION", target.id(), edge.id(), path,
                        "The edge kind, named ports, or registered port schemas do not match.");
            } else {
                validEdges.add(edge);
            }
        }
        List<Node> inputs = nodes.values().stream().filter(n -> "workflow.input".equals(n.type())).toList();
        if (inputs.size() != 1) {
            error(errors, "INPUT_COUNT", null, null, "/nodes", "Exactly one workflow input is required.");
        }
        if (nodes.values().stream().filter(n -> "logic.switch".equals(n.type())).count() > 1) {
            error(errors, "SWITCH_LIMIT", null, null, "/nodes", "The first compiler supports at most one switch.");
        }
        if (cycle(nodes.keySet(), validEdges, "control")) {
            error(errors, "CONTROL_CYCLE", null, null, "/edges", "Control cycles are unsupported.");
        }
        if (cycle(nodes.keySet(), validEdges, "data")) {
            error(errors, "DATA_CYCLE", null, null, "/edges", "Data cycles are unsupported.");
        }
        Set<String> reachable = inputs.size() == 1 ? reachable(inputs.getFirst().id(), validEdges) : Set.of();
        for (Node node : nodes.values()) {
            var descriptor = catalog.get(node.type());
            if (descriptor == null) {
                continue;
            }
            List<Edge> incoming = edges(validEdges, node.id(), "control", true);
            List<Edge> outgoing = edges(validEdges, node.id(), "control", false);
            if (descriptor.executable()) {
                int expectedIn = "workflow.input".equals(node.type()) ? 0 : 1;
                int expectedOut = descriptor.controlOutputs().size();
                if (incoming.size() != expectedIn || outgoing.size() != expectedOut
                        || outgoing.stream().map(Edge::sourcePort).distinct().count() != expectedOut) {
                    error(errors, "CONTROL_CARDINALITY", node.id(), null, "/nodes",
                            "Connect each declared control outcome once; joins and fan-out are unsupported.");
                }
                if (!reachable.contains(node.id())) {
                    error(errors, "UNREACHABLE_NODE", node.id(), null, "/nodes", "Executable node is unreachable.");
                }
                for (var port : descriptor.inputPorts()) {
                    long count = edges(validEdges, node.id(), "data", true).stream()
                            .filter(e -> port.id().equals(e.targetPort())).count();
                    if (count != 1) {
                        error(errors, "DATA_CARDINALITY", node.id(), null, "/nodes",
                                "Input " + port.id() + " requires exactly one binding.");
                    }
                }
                for (String capability : descriptor.capabilityInputs().keySet()) {
                    long count = edges(validEdges, node.id(), "capability", true).stream()
                            .filter(e -> capability.equals(e.targetPort())).count();
                    if (count > 1 || (descriptor.capabilityInputs().get(capability).required() && count != 1)) {
                        error(errors, "CAPABILITY_CARDINALITY", node.id(), null, "/nodes",
                                "Attach one registered " + capability + " capability.");
                    }
                }
            } else if (edges(validEdges, node.id(), "capability", false).isEmpty()
                    || !incoming.isEmpty() || !outgoing.isEmpty()) {
                error(errors, "UNUSED_CAPABILITY", node.id(), null, "/nodes", "Connect the capability to an agent.");
            }
        }
        if (nodes.values().stream().noneMatch(n -> "workflow.output".equals(n.type()) && reachable.contains(n.id()))) {
            error(errors, "MISSING_OUTPUT", null, null, "/nodes", "A reachable output is required.");
        }
        for (Edge edge : validEdges) {
            if ("data".equals(edge.kind()) && !dominates(edge.source(), edge.target(), validEdges)) {
                error(errors, "UNAVAILABLE_DATA", edge.target(), edge.id(), "/edges",
                        "The source must execute before the consumer on every supported control path.");
            }
        }
        return new Validation(errors.isEmpty(), List.copyOf(errors));
    }

    private static boolean compatiblePorts(NodeCatalog.Descriptor from, NodeCatalog.Descriptor to, Edge edge) {
        return from.outputPorts().stream().anyMatch(source -> source.id().equals(edge.sourcePort())
                && to.inputPorts().stream().anyMatch(target -> target.id().equals(edge.targetPort())
                        && source.schemaRef().equals(target.schemaRef())));
    }

    static List<Edge> edges(List<Edge> edges, String id, String kind, boolean incoming) {
        return edges.stream().filter(e -> kind.equals(e.kind())
                && id.equals(incoming ? e.target() : e.source())).toList();
    }

    private static Set<String> reachable(String input, List<Edge> edges) {
        var result = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(input);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (result.add(current)) {
                for (Edge edge : edges(edges, current, "control", false)) {
                    queue.add(edge.target());
                }
            }
        }
        return result;
    }

    private static boolean dominates(String source, String target, List<Edge> edges) {
        var seen = new HashSet<String>();
        String current = target;
        while (seen.add(current)) {
            List<Edge> parents = edges(edges, current, "control", true);
            if (parents.size() != 1) {
                return false;
            }
            current = parents.getFirst().source();
            if (current.equals(source)) {
                return true;
            }
        }
        return false;
    }

    private static boolean cycle(Set<String> ids, List<Edge> edges, String kind) {
        var degrees = new HashMap<String, Integer>();
        ids.forEach(id -> degrees.put(id, 0));
        for (Edge edge : edges) {
            if (kind.equals(edge.kind())) {
                degrees.merge(edge.target(), 1, Integer::sum);
            }
        }
        var ready = new ArrayDeque<String>();
        degrees.forEach((id, count) -> { if (count == 0) ready.add(id); });
        int visited = 0;
        while (!ready.isEmpty()) {
            String id = ready.remove();
            visited++;
            for (Edge edge : edges(edges, id, kind, false)) {
                if (degrees.merge(edge.target(), -1, Integer::sum) == 0) {
                    ready.add(edge.target());
                }
            }
        }
        return visited != ids.size();
    }

    private static void error(List<Diagnostic> errors, String code, String node, String edge, String path, String message) {
        errors.add(new Diagnostic(code, node, edge, path, message));
    }

    public record Diagnostic(String code, String nodeId, String edgeId, String path, String message) {
    }

    public record Validation(boolean valid, List<Diagnostic> errors) {
    }
}
