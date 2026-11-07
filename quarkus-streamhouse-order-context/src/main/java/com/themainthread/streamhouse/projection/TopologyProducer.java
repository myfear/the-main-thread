package com.themainthread.streamhouse.projection;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.jboss.logging.Logger;

import com.themainthread.streamhouse.event.EventValidation;
import com.themainthread.streamhouse.event.OrderEvent;
import com.themainthread.streamhouse.event.SourceHeartbeat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class TopologyProducer {
    public static final String ORDERS = "order-context-store";
    public static final String HEARTBEATS = "source-heartbeat-store";
    private static final Logger LOG = Logger.getLogger(TopologyProducer.class);
    private final LivenessEvaluation liveness;

    public TopologyProducer(LivenessEvaluation liveness) {
        this.liveness = liveness;
    }

    @Produces
    public Topology topology() {
        StreamsBuilder builder = new StreamsBuilder();
        var checkout = builder.stream("order-placed", Consumed.with(Serdes.String(), JsonSerdes.checkout()))
                .mapValues((key, value) -> OrderEvent.from(key, value));
        var inventory = builder.stream("inventory-status", Consumed.with(Serdes.String(), JsonSerdes.inventory()))
                .mapValues((key, value) -> OrderEvent.from(key, value));
        var shipping = builder.stream("shipping-status", Consumed.with(Serdes.String(), JsonSerdes.shipping()))
                .mapValues((key, value) -> OrderEvent.from(key, value));

        checkout.merge(inventory).merge(shipping)
                .groupByKey(Grouped.with(Serdes.String(), JsonSerdes.event()))
                .aggregate(OrderState::empty, (key, event, state) -> OrderReducer.apply(state, event),
                        Materialized.<String, OrderState, KeyValueStore<Bytes, byte[]>>as(ORDERS)
                                .withKeySerde(Serdes.String()).withValueSerde(JsonSerdes.state()));

        builder.stream("source-heartbeats", Consumed.with(Serdes.String(), JsonSerdes.heartbeat()))
                .peek((key, heartbeat) -> EventValidation.required(heartbeat, "heartbeat").validateKey(key))
                .filter((key, heartbeat) -> {
                    if (liveness.acceptable(heartbeat)) {
                        return true;
                    }
                    LOG.warnf("Rejected future heartbeat from %s at %s", key, heartbeat.emittedAt());
                    return false;
                })
                .groupByKey(Grouped.with(Serdes.String(), JsonSerdes.heartbeat()))
                .reduce((current, incoming) -> incoming.emittedAt().isAfter(current.emittedAt()) ? incoming : current,
                        Materialized.<String, SourceHeartbeat, KeyValueStore<Bytes, byte[]>>as(HEARTBEATS)
                                .withKeySerde(Serdes.String()).withValueSerde(JsonSerdes.heartbeat()));
        return builder.build();
    }
}
