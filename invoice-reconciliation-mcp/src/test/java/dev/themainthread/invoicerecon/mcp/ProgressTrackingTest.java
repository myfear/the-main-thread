package dev.themainthread.invoicerecon.mcp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.themainthread.invoicerecon.domain.MissingGoodsReceiptAction;
import dev.themainthread.invoicerecon.policy.ReconciliationPolicy;
import dev.themainthread.invoicerecon.service.ReconciliationService;
import dev.themainthread.invoicerecon.support.TestCancellation;
import dev.themainthread.invoicerecon.support.TestDataCleaner;

import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.ProgressNotification;
import io.quarkiverse.mcp.server.ProgressToken;
import io.quarkiverse.mcp.server.ProgressTracker;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class ProgressTrackingTest {

    @Inject
    ReconciliationService reconciliationService;

    @Inject
    TestDataCleaner testDataCleaner;

    @BeforeEach
    void resetData() {
        testDataCleaner.resetReconciliationState();
    }

    @Test
    void progressValuesIncreaseMonotonically() {
        RecordingProgress progress = new RecordingProgress();

        reconciliationService.runBatch(
                "ACME",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                new ReconciliationPolicy(2.5, MissingGoodsReceiptAction.FLAG_FOR_REVIEW, false, "FIN-OPERATIONS"),
                progress,
                new TestCancellation(Integer.MAX_VALUE));

        List<BigDecimal> values = progress.progressValues();
        assertTrue(values.size() >= 3, "Expected phase and invoice progress notifications");
        assertTrue(values.get(values.size() - 1).compareTo(values.get(0)) > 0);
    }

    private static final class RecordingProgress implements Progress {

        private final List<BigDecimal> progressValues = new ArrayList<>();
        private final AtomicInteger invoiceProgress = new AtomicInteger();

        @Override
        public Optional<ProgressToken> token() {
            return Optional.of(new ProgressToken("test-progress-token"));
        }

        @Override
        public ProgressNotification.Builder notificationBuilder() {
            return new ProgressNotification.Builder() {
                private BigDecimal progress = BigDecimal.ZERO;
                private String message;

                @Override
                public ProgressNotification.Builder setProgress(long value) {
                    this.progress = BigDecimal.valueOf(value);
                    return this;
                }

                @Override
                public ProgressNotification.Builder setProgress(double value) {
                    this.progress = BigDecimal.valueOf(value);
                    return this;
                }

                @Override
                public ProgressNotification.Builder setTotal(long value) {
                    return this;
                }

                @Override
                public ProgressNotification.Builder setTotal(double value) {
                    return this;
                }

                @Override
                public ProgressNotification.Builder setMessage(String value) {
                    this.message = value;
                    return this;
                }

                @Override
                public ProgressNotification build() {
                    return new ProgressNotification() {
                        @Override
                        public ProgressToken token() {
                            return new ProgressToken("test-progress-token");
                        }

                        @Override
                        public BigDecimal total() {
                            return BigDecimal.valueOf(5);
                        }

                        @Override
                        public BigDecimal progress() {
                            return progress;
                        }

                        @Override
                        public String message() {
                            return message;
                        }

                        @Override
                        public void sendAndForget() {
                            progressValues.add(progress);
                        }

                        @Override
                        public io.smallrye.mutiny.Uni<Void> send() {
                            sendAndForget();
                            return io.smallrye.mutiny.Uni.createFrom().voidItem();
                        }
                    };
                }
            };
        }

        @Override
        public ProgressTracker.Builder trackerBuilder() {
            return new ProgressTracker.Builder() {
                private int total = 32;
                private int step = 8;

                @Override
                public ProgressTracker.Builder setTotal(long value) {
                    total = (int) value;
                    return this;
                }

                @Override
                public ProgressTracker.Builder setTotal(double value) {
                    total = (int) value;
                    return this;
                }

                @Override
                public ProgressTracker.Builder setDefaultStep(long value) {
                    step = (int) value;
                    return this;
                }

                @Override
                public ProgressTracker.Builder setDefaultStep(double value) {
                    step = (int) value;
                    return this;
                }

                @Override
                public ProgressTracker.Builder setMessageBuilder(
                        java.util.function.Function<BigDecimal, String> builder) {
                    return this;
                }

                @Override
                public ProgressTracker build() {
                    return new ProgressTracker() {
                        @Override
                        public ProgressToken token() {
                            return new ProgressToken("test-progress-token");
                        }

                        @Override
                        public void advanceAndForget(BigDecimal amount) {
                            progressValues.add(BigDecimal.valueOf(invoiceProgress.addAndGet(amount.intValue())));
                        }

                        @Override
                        public io.smallrye.mutiny.Uni<Void> advance(BigDecimal amount) {
                            advanceAndForget(amount);
                            return io.smallrye.mutiny.Uni.createFrom().voidItem();
                        }

                        @Override
                        public BigDecimal progress() {
                            return BigDecimal.valueOf(invoiceProgress.get());
                        }

                        @Override
                        public BigDecimal total() {
                            return BigDecimal.valueOf(total);
                        }

                        @Override
                        public BigDecimal step() {
                            return BigDecimal.valueOf(step);
                        }
                    };
                }
            };
        }

        List<BigDecimal> progressValues() {
            return progressValues;
        }
    }
}
