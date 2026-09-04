package com.themainthread.terminal.dispatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DispatchService {

    private final Map<String, Hub> hubs = new LinkedHashMap<>();

    public DispatchService() {
        reset();
    }

    public synchronized void reset() {
        hubs.clear();

        Hub berlin = new Hub("berlin", 184, true);
        berlin.failures.put("SHP-1042",
                new ShipmentFailure("SHP-1042", "carrier timeout", 3, "fallback-dhl", ShipmentState.FAILED));
        berlin.failures.put("SHP-1077",
                new ShipmentFailure("SHP-1077", "label rejected", 1, "manual-review", ShipmentState.FAILED));
        hubs.put(berlin.name, berlin);

        hubs.put("madrid", new Hub("madrid", 12, true));
        hubs.put("oslo", new Hub("oslo", 7, true));
    }

    public synchronized List<HubSnapshot> hubs() {
        return hubs.values().stream()
                .map(this::snapshot)
                .sorted(Comparator.comparing(HubSnapshot::name))
                .toList();
    }

    public synchronized Optional<HubSnapshot> hub(String name) {
        return Optional.ofNullable(hubs.get(normalize(name))).map(this::snapshot);
    }

    public synchronized boolean hasHub(String name) {
        return hubs.containsKey(normalize(name));
    }

    public synchronized List<String> hubNames() {
        return hubs.keySet().stream().sorted().toList();
    }

    public synchronized List<ShipmentFailure> failures(String hubName) {
        Hub hub = hubs.get(normalize(hubName));
        if (hub == null) {
            return List.of();
        }
        return hub.failures.values().stream()
                .filter(failure -> failure.state() == ShipmentState.FAILED)
                .sorted(Comparator.comparing(ShipmentFailure::id))
                .toList();
    }

    public synchronized List<String> failedShipmentIds() {
        List<String> ids = new ArrayList<>();
        for (Hub hub : hubs.values()) {
            hub.failures.values().stream()
                    .filter(failure -> failure.state() == ShipmentState.FAILED)
                    .map(ShipmentFailure::id)
                    .forEach(ids::add);
        }
        return ids.stream().sorted().toList();
    }

    public synchronized OperationResult retry(String hubName, String shipmentId, boolean dryRun, String confirmation) {
        Hub hub = hubs.get(normalize(hubName));
        if (hub == null) {
            return OperationResult.failure("Unknown hub '" + hubName + "'.");
        }

        String normalizedId = normalizeId(shipmentId);
        ShipmentFailure failure = hub.failures.get(normalizedId);
        if (failure == null) {
            return OperationResult.failure("Shipment " + normalizedId + " is not part of this hub.");
        }
        if (failure.state() == ShipmentState.RETRY_QUEUED) {
            return OperationResult.success("Shipment " + normalizedId + " is already queued for retry.");
        }

        if (dryRun) {
            return OperationResult.success("Would retry " + normalizedId + " through " + failure.nextRoute() + ".");
        }
        if (!normalizedId.equals(normalizeId(confirmation))) {
            return OperationResult.failure("Retry refused. Pass --confirm=" + normalizedId + ".");
        }

        hub.failures.put(normalizedId, failure.withState(ShipmentState.RETRY_QUEUED));
        hub.queued++;
        return OperationResult.success("Shipment " + normalizedId + " queued through " + failure.nextRoute() + ".");
    }

    public synchronized OperationResult drain(String hubName, String confirmation) {
        Hub hub = hubs.get(normalize(hubName));
        if (hub == null) {
            return OperationResult.failure("Unknown hub '" + hubName + "'.");
        }
        if (!hub.name.equals(normalize(confirmation))) {
            return OperationResult.failure("Drain refused. Pass --confirm=" + hub.name + ".");
        }
        if (!hub.acceptingTraffic) {
            return OperationResult.success("Hub " + hub.name + " is already drained.");
        }

        hub.acceptingTraffic = false;
        return OperationResult.success("Hub " + hub.name + " stopped accepting new traffic.");
    }

    public synchronized OperationResult resume(String hubName, String confirmation) {
        Hub hub = hubs.get(normalize(hubName));
        if (hub == null) {
            return OperationResult.failure("Unknown hub '" + hubName + "'.");
        }
        if (!hub.name.equals(normalize(confirmation))) {
            return OperationResult.failure("Resume refused. Pass --confirm=" + hub.name + ".");
        }
        if (hub.acceptingTraffic) {
            return OperationResult.success("Hub " + hub.name + " is already accepting traffic.");
        }

        hub.acceptingTraffic = true;
        return OperationResult.success("Hub " + hub.name + " resumed traffic.");
    }

    private HubSnapshot snapshot(Hub hub) {
        long failed = hub.failures.values().stream()
                .filter(failure -> failure.state() == ShipmentState.FAILED)
                .count();
        HubState state = !hub.acceptingTraffic ? HubState.DRAINED : failed > 0 ? HubState.DEGRADED : HubState.HEALTHY;
        return new HubSnapshot(hub.name, state, hub.queued, failed, hub.acceptingTraffic);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public enum HubState {
        HEALTHY,
        DEGRADED,
        DRAINED
    }

    public enum ShipmentState {
        FAILED,
        RETRY_QUEUED
    }

    public record HubSnapshot(String name, HubState state, int queued, long failed, boolean acceptingTraffic) {
    }

    public record ShipmentFailure(String id, String reason, int attempts, String nextRoute, ShipmentState state) {
        ShipmentFailure withState(ShipmentState newState) {
            return new ShipmentFailure(id, reason, attempts, nextRoute, newState);
        }
    }

    public record OperationResult(boolean successful, String message) {
        static OperationResult success(String message) {
            return new OperationResult(true, message);
        }

        static OperationResult failure(String message) {
            return new OperationResult(false, message);
        }
    }

    private static final class Hub {
        private final String name;
        private final Map<String, ShipmentFailure> failures = new LinkedHashMap<>();
        private int queued;
        private boolean acceptingTraffic;

        private Hub(String name, int queued, boolean acceptingTraffic) {
            this.name = name;
            this.queued = queued;
            this.acceptingTraffic = acceptingTraffic;
        }
    }
}
