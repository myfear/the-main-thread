package dev.quarkex.nebulatrack;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.model.CostEstimate;
import dev.quarkex.nebulatrack.model.UnmatchedEstimateRequest;
import dev.quarkex.nebulatrack.service.BudgetService;
import dev.quarkex.nebulatrack.service.CostMonitor;
import dev.quarkex.nebulatrack.service.CriticalAnomalyEmitter;
import dev.quarkex.nebulatrack.service.PluginReceiverRegistrar;
import dev.quarkex.nebulatrack.service.RemediationDispatcher;
import dev.quarkex.nebulatrack.support.InMemoryLedger;
import io.quarkus.signals.Receivers;
import io.quarkus.signals.Signal;
import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class NebulaTrackSignalsTest {

    @Inject
    CostMonitor costMonitor;

    @Inject
    RemediationDispatcher remediationDispatcher;

    @Inject
    BudgetService budgetService;

    @Inject
    CriticalAnomalyEmitter criticalAnomalyEmitter;

    @Inject
    PluginReceiverRegistrar pluginReceiverRegistrar;

    @Inject
    InMemoryLedger ledger;

    @Inject
    Signal<UnmatchedEstimateRequest> unmatchedSignal;

    @BeforeEach
    void resetLedger() {
        ledger.reset();
    }

    @Test
    void publishNotifiesAllReceivers() {
        costMonitor.detect();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(ledger.alertCount()).isGreaterThanOrEqualTo(1);
            assertThat(ledger.auditCount()).isGreaterThanOrEqualTo(1);
            assertThat(ledger.dashboardCount()).isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    void defaultReceiverIgnoresCriticalLane() {
        criticalAnomalyEmitter.publishCritical();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(ledger.criticalLaneCount()).isGreaterThanOrEqualTo(1);
            assertThat(ledger.defaultLaneCount()).isZero();
        });
    }

    @Test
    void sendRoundRobinsBetweenWorkers() {
        for (int i = 0; i < 6; i++) {
            remediationDispatcher.dispatch("us-east-1", "scale-down-idle-nodes");
        }

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            int total = ledger.workerACount() + ledger.workerBCount();
            assertThat(total).isEqualTo(6);
            assertThat(Math.abs(ledger.workerACount() - ledger.workerBCount())).isLessThanOrEqualTo(1);
        });
    }

    @Test
    void requestReturnsTypedEstimate() {
        CostEstimate estimate = budgetService.estimateBlocking("s3", 500);

        assertThat(estimate).isNotNull();
        assertThat(estimate.service()).isEqualTo("s3");
        assertThat(estimate.units()).isEqualTo(500);
        assertThat(estimate.monthlyCost()).isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    void requestReturnsTypedEstimateReactive() {
        CostEstimate estimate = budgetService.estimateReactive("ec2", 100)
                .await().atMost(java.time.Duration.ofSeconds(10));

        assertThat(estimate).isNotNull();
        assertThat(estimate.service()).isEqualTo("ec2");
        assertThat(estimate.monthlyCost()).isEqualByComparingTo(new BigDecimal("12.00"));
    }

    @Test
    void requestReturnsNullWhenNoReceiver() {
        CostEstimate estimate = unmatchedSignal.request(
                new UnmatchedEstimateRequest("lambda", 10), CostEstimate.class);

        assertThat(estimate).isNull();
    }

    @Test
    void criticalLaneAndCatchAllReceiver() {
        criticalAnomalyEmitter.publishDefault();
        criticalAnomalyEmitter.publishCritical();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(ledger.defaultLaneCount()).isGreaterThanOrEqualTo(1);
            assertThat(ledger.criticalLaneCount()).isGreaterThanOrEqualTo(1);
            assertThat(ledger.catchAllCount()).isGreaterThanOrEqualTo(2);
        });
    }

    @Test
    void metadataVisibleInReceiver() {
        costMonitor.detectWithMetadata("abc-123", "acme");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(ledger.metadataSnapshots()).isNotEmpty();
            assertThat(ledger.metadataSnapshots().getFirst()).containsEntry("traceId", "abc-123");
            assertThat(ledger.metadataSnapshots().getFirst()).containsEntry("tenant", "acme");
        });
    }

    @Test
    void programmaticRegisterAndUnregister() {
        Receivers.Registration registration = pluginReceiverRegistrar.register(
                anomaly -> ledger.recordPlugin());

        criticalAnomalyEmitter.publishCritical();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(ledger.pluginCount()).isEqualTo(1));

        registration.unregister();
        int afterFirst = ledger.pluginCount();
        criticalAnomalyEmitter.publishCritical();

        await().pollDelay(500, TimeUnit.MILLISECONDS)
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(ledger.pluginCount()).isEqualTo(afterFirst));
    }

    @Test
    void receiversGetIsolatedRequestScope() {
        costMonitor.detect();
        costMonitor.detect();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(ledger.requestScopeIds()).hasSizeGreaterThanOrEqualTo(2));

        assertThat(ledger.requestScopeIds().get(0))
                .isNotEqualTo(ledger.requestScopeIds().get(1));
    }
}
