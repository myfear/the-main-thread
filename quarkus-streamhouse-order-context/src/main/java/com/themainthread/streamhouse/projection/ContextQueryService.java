package com.themainthread.streamhouse.projection;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kafka.streams.errors.InvalidStateStoreException;

import com.themainthread.streamhouse.api.ApiError;
import com.themainthread.streamhouse.api.OrderContextResponse;
import com.themainthread.streamhouse.event.EventValidation;
import com.themainthread.streamhouse.event.SourceHeartbeat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class ContextQueryService {
    private final StateStoreReader stores;
    private final LivenessEvaluation liveness;

    public ContextQueryService(StateStoreReader stores, LivenessEvaluation liveness) {
        this.stores = stores;
        this.liveness = liveness;
    }

    public OrderContextResponse query(long orderId) {
        EventValidation.positive(orderId, "orderId");
        StateStoreReader.Snapshot snapshot;
        try {
            snapshot = stores.read(Long.toString(orderId));
        } catch (InvalidStateStoreException | IllegalStateException unavailable) {
            throw new WebApplicationException(Response.status(503).header("Retry-After", "1")
                    .entity(new ApiError("STORE_UNAVAILABLE", "Order context is temporarily unavailable")).build());
        }
        if (snapshot.order() == null) {
            throw new WebApplicationException(Response.status(404)
                    .entity(new ApiError("ORDER_NOT_FOUND", "No processed events for this order")).build());
        }
        Instant now = liveness.now();
        Map<String, LivenessEvaluation.Signal> signals = new LinkedHashMap<>();
        for (String source : SourceHeartbeat.SOURCES) {
            signals.put(source, liveness.evaluate(snapshot.heartbeats().get(source), now));
        }
        return OrderContextResponse.from(snapshot.order(), signals, snapshot.processorState());
    }
}
