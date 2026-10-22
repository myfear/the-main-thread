package com.themainthread.checkout;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OutboxPublisher {

    private static final Logger LOG = Logger.getLogger(OutboxPublisher.class);
    private final ObjectMapper json;
    private final OrderPlacedProducer producer;
    private final CrashSwitch crashes;
    private final OutboxClaims claims;
    private final CheckoutConfig.Outbox config;
    private final String workerId;

    OutboxPublisher(ObjectMapper json, OrderPlacedProducer producer, CrashSwitch crashes,
            OutboxClaims claims, CheckoutConfig config) {
        this.json = json;
        this.producer = producer;
        this.crashes = crashes;
        this.claims = claims;
        this.config = config.outbox();
        this.workerId = this.config.workerLabel() + "/" + UUID.randomUUID();
        if (this.config.pollEvery().compareTo(Duration.ofMillis(250)) < 0) {
            throw new IllegalArgumentException("checkout.outbox.poll-every must be at least 250ms");
        }
    }

    @Scheduled(every = "{checkout.outbox.poll-every}", identity = "outbox-publisher",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void poll() {
        publishPending();
    }

    @Transactional(Transactional.TxType.NEVER)
    public int publishPending() {
        requireNoTransaction();
        return publish(claims.claim(workerId, config.batchSize(), config.leaseDuration(), null));
    }

    @Transactional(Transactional.TxType.NEVER)
    public boolean publishOne(UUID id) {
        requireNoTransaction();
        return publish(claims.claim(workerId, 1, config.leaseDuration(), id)) == 1;
    }

    public String workerId() {
        return workerId;
    }

    private int publish(List<OutboxClaim> batch) {
        int completed = 0;
        for (OutboxClaim claim : batch) {
            LOG.infof("CLAIM worker=%s event=%s token=%s until=%s", workerId, claim.id(), claim.token(), claim.until());
            // The REQUIRES_NEW interceptor has committed before claim() returns.
            requireNoTransaction();
            crashes.maybeCrash(CrashPoint.CLAIM);
            try {
                OrderPlaced event = json.readValue(claim.payload(), OrderPlaced.class);
                producer.send(event);
                crashes.maybeCrash(CrashPoint.KAFKA);
                if (claims.complete(claim.id(), claim.token()) == 1) {
                    completed++;
                    LOG.infof("COMPLETE worker=%s event=%s token=%s", workerId, claim.id(), claim.token());
                } else {
                    LOG.warnf("STALE worker=%s event=%s token=%s", workerId, claim.id(), claim.token());
                }
            } catch (CrashWindowException e) {
                throw e;
            } catch (JsonProcessingException | RuntimeException e) {
                // No early release: the lease supplies a minimum retry interval.
                LOG.errorf(e, "RETRY_AFTER_LEASE worker=%s event=%s token=%s", workerId, claim.id(), claim.token());
            }
        }
        return completed;
    }

    static void requireNoTransaction() {
        if (QuarkusTransaction.isActive()) {
            throw new IllegalStateException("Kafka publication must run outside a database transaction");
        }
    }
}
