package com.themainthread.streamhouse.projection;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;

import com.themainthread.streamhouse.event.SourceHeartbeat;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StateStoreReader {
    public record Snapshot(OrderState order, Map<String, SourceHeartbeat> heartbeats, String processorState) {
    }

    private final KafkaStreams streams;

    public StateStoreReader(KafkaStreams streams) {
        this.streams = streams;
    }

    public Snapshot read(String key) {
        requireRunning();
        var orders = streams.store(StoreQueryParameters.fromNameAndType(TopologyProducer.ORDERS,
                QueryableStoreTypes.<String, OrderState>keyValueStore()));
        var heartbeats = streams.store(StoreQueryParameters.fromNameAndType(TopologyProducer.HEARTBEATS,
                QueryableStoreTypes.<String, SourceHeartbeat>keyValueStore()));
        OrderState order = orders.get(key);
        Map<String, SourceHeartbeat> signals = new LinkedHashMap<>();
        for (String source : SourceHeartbeat.SOURCES) {
            SourceHeartbeat heartbeat = heartbeats.get(source);
            if (heartbeat != null) {
                signals.put(source, heartbeat);
            }
        }
        requireRunning();
        return new Snapshot(order, Map.copyOf(signals), KafkaStreams.State.RUNNING.name());
    }

    private void requireRunning() {
        if (streams.state() != KafkaStreams.State.RUNNING) {
            throw new InvalidStateStoreException("Kafka Streams is not running");
        }
    }
}
