package com.themainthread.checkout;

import java.time.Instant;
import java.util.UUID;

public record OutboxClaim(UUID id, String aggregateId, String payload, UUID token, Instant until) {
}
