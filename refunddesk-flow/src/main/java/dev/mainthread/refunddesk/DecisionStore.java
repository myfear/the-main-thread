package dev.mainthread.refunddesk;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DecisionStore {

    private final ConcurrentMap<String, RefundResult> results = new ConcurrentHashMap<>();

    public void recordAutomatic(RefundCase refundCase) {
        RefundRequest request = refundCase.request();
        RefundDecision decision = refundCase.decision();

        results.put(request.refundId(),
                new RefundResult(request.refundId(), decision.outcome(), decision.reason(), "policy"));
    }

    public void recordManualReview(ReviewDecision review) {
        if (DecisionOutcome.MANUAL_REVIEW.equals(review.outcome())) {
            throw new IllegalArgumentException("A reviewer must approve or deny the refund");
        }

        results.put(review.refundId(),
                new RefundResult(review.refundId(), review.outcome(), review.note(), review.reviewer()));
    }

    public Optional<RefundResult> find(String refundId) {
        return Optional.ofNullable(results.get(refundId));
    }
}
