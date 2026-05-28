package dev.quarkex.nebulatrack.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;

import dev.quarkex.nebulatrack.model.CostAnomaly;

@ApplicationScoped
public class InMemoryLedger {

    private final CopyOnWriteArrayList<CostAnomaly> anomalyEvents = new CopyOnWriteArrayList<>();
    private final AtomicInteger alertCount = new AtomicInteger();
    private final AtomicInteger auditCount = new AtomicInteger();
    private final AtomicInteger dashboardCount = new AtomicInteger();
    private final AtomicInteger workerACount = new AtomicInteger();
    private final AtomicInteger workerBCount = new AtomicInteger();
    private final AtomicInteger defaultLaneCount = new AtomicInteger();
    private final AtomicInteger criticalLaneCount = new AtomicInteger();
    private final AtomicInteger catchAllCount = new AtomicInteger();
    private final AtomicInteger pluginCount = new AtomicInteger();
    private final CopyOnWriteArrayList<Map<String, Object>> metadataSnapshots = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<UUID> requestScopeIds = new CopyOnWriteArrayList<>();

    public void recordAnomaly(CostAnomaly anomaly) {
        anomalyEvents.add(anomaly);
    }

    public void recordAlert() {
        alertCount.incrementAndGet();
    }

    public void recordAudit() {
        auditCount.incrementAndGet();
    }

    public void recordDashboardRefresh() {
        dashboardCount.incrementAndGet();
    }

    public void recordWorkerA() {
        workerACount.incrementAndGet();
    }

    public void recordWorkerB() {
        workerBCount.incrementAndGet();
    }

    public void recordDefaultLane() {
        defaultLaneCount.incrementAndGet();
    }

    public void recordCriticalLane() {
        criticalLaneCount.incrementAndGet();
    }

    public void recordCatchAll() {
        catchAllCount.incrementAndGet();
    }

    public void recordPlugin() {
        pluginCount.incrementAndGet();
    }

    public void recordMetadata(Map<String, Object> metadata) {
        metadataSnapshots.add(Map.copyOf(metadata));
    }

    public void recordRequestScopeId(UUID id) {
        requestScopeIds.add(id);
    }

    public List<CostAnomaly> anomalyEvents() {
        return Collections.unmodifiableList(new ArrayList<>(anomalyEvents));
    }

    public int alertCount() {
        return alertCount.get();
    }

    public int auditCount() {
        return auditCount.get();
    }

    public int dashboardCount() {
        return dashboardCount.get();
    }

    public int workerACount() {
        return workerACount.get();
    }

    public int workerBCount() {
        return workerBCount.get();
    }

    public int defaultLaneCount() {
        return defaultLaneCount.get();
    }

    public int criticalLaneCount() {
        return criticalLaneCount.get();
    }

    public int catchAllCount() {
        return catchAllCount.get();
    }

    public int pluginCount() {
        return pluginCount.get();
    }

    public List<Map<String, Object>> metadataSnapshots() {
        return Collections.unmodifiableList(new ArrayList<>(metadataSnapshots));
    }

    public List<UUID> requestScopeIds() {
        return Collections.unmodifiableList(new ArrayList<>(requestScopeIds));
    }

    public void reset() {
        anomalyEvents.clear();
        alertCount.set(0);
        auditCount.set(0);
        dashboardCount.set(0);
        workerACount.set(0);
        workerBCount.set(0);
        defaultLaneCount.set(0);
        criticalLaneCount.set(0);
        catchAllCount.set(0);
        pluginCount.set(0);
        metadataSnapshots.clear();
        requestScopeIds.clear();
    }
}
