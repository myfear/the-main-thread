package com.themainthread.streamhouse;

import static com.themainthread.streamhouse.event.InventoryStatusChanged.Status.RESERVED;
import static com.themainthread.streamhouse.event.ShippingStatusChanged.Status.DELAYED;
import static com.themainthread.streamhouse.event.ShippingStatusChanged.Status.PACKED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Properties;

import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.themainthread.streamhouse.event.InventoryStatusChanged;
import com.themainthread.streamhouse.event.OrderPlaced;
import com.themainthread.streamhouse.event.ShippingStatusChanged;
import com.themainthread.streamhouse.event.SourceHeartbeat;
import com.themainthread.streamhouse.projection.JsonSerdes;
import com.themainthread.streamhouse.projection.LivenessEvaluation;
import com.themainthread.streamhouse.projection.OrderState;
import com.themainthread.streamhouse.projection.TopologyProducer;

class OrderTopologyTest {
    @TempDir
    Path directory;
    private TopologyTestDriver driver;
    private TestInputTopic<String, OrderPlaced> checkout;
    private TestInputTopic<String, InventoryStatusChanged> inventory;
    private TestInputTopic<String, ShippingStatusChanged> shipping;
    private TestInputTopic<String, SourceHeartbeat> heartbeats;
    private KeyValueStore<String, OrderState> orders;

    @BeforeEach
    void start() {
        var liveness = new LivenessEvaluation(Clock.fixed(Fixtures.TIME, ZoneOffset.UTC),
                Duration.ofSeconds(30), Duration.ofSeconds(5));
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "topology-test");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, directory.toString());
        driver = new TopologyTestDriver(new TopologyProducer(liveness).topology(), properties, Fixtures.TIME);
        checkout = driver.createInputTopic("order-placed", new StringSerializer(), JsonSerdes.checkout().serializer());
        inventory = driver.createInputTopic("inventory-status", new StringSerializer(), JsonSerdes.inventory().serializer());
        shipping = driver.createInputTopic("shipping-status", new StringSerializer(), JsonSerdes.shipping().serializer());
        heartbeats = driver.createInputTopic("source-heartbeats", new StringSerializer(), JsonSerdes.heartbeat().serializer());
        orders = driver.getKeyValueStore(TopologyProducer.ORDERS);
    }

    @AfterEach
    void stop() {
        driver.close();
    }

    @Test
    void interleavedTopicsMaterializePartialAndCompleteOrdersWithoutRegression() {
        shipping.pipeInput("1042", Fixtures.shipping(1042, 2, DELAYED));
        assertEquals(DELAYED, orders.get("1042").shipping().status());
        assertFalse(orders.get("1042").complete());
        checkout.pipeInput("1043", Fixtures.checkout(1043));
        inventory.pipeInput("1042", Fixtures.inventory(1042, 1, RESERVED));
        shipping.pipeInput("1042", Fixtures.shipping(1042, 1, PACKED));
        checkout.pipeInput("1042", Fixtures.checkout(1042));
        shipping.pipeInput("1042", Fixtures.shipping(1042, 2, DELAYED));
        assertTrue(orders.get("1042").complete());
        assertEquals(2, orders.get("1042").shipping().revision());
        assertFalse(orders.get("1043").complete());
        assertNull(orders.get("1043").shipping());
        assertNull(orders.get("9999"));
    }

    @Test
    void conflictIsStoredWithoutReplacingTheFact() {
        shipping.pipeInput("1042", Fixtures.shipping(1042, 2, DELAYED));
        shipping.pipeInput("1042", Fixtures.shipping(1042, 2, PACKED));
        assertTrue(orders.get("1042").conflict());
        assertEquals(DELAYED, orders.get("1042").shipping().status());
    }

    @Test
    void heartbeatStoreRejectsFutureProgressAndRetainsHighestValidTimestamp() {
        KeyValueStore<String, SourceHeartbeat> store = driver.getKeyValueStore(TopologyProducer.HEARTBEATS);
        heartbeats.pipeInput("shipping", new SourceHeartbeat("shipping", Fixtures.TIME.plusSeconds(3600)));
        assertNull(store.get("shipping"));
        heartbeats.pipeInput("shipping", new SourceHeartbeat("shipping", Fixtures.TIME.minusSeconds(10)));
        heartbeats.pipeInput("shipping", new SourceHeartbeat("shipping", Fixtures.TIME.plusSeconds(3600)));
        assertEquals(Fixtures.TIME.minusSeconds(10), store.get("shipping").emittedAt());
        heartbeats.pipeInput("shipping", new SourceHeartbeat("shipping", Fixtures.TIME));
        heartbeats.pipeInput("shipping", new SourceHeartbeat("shipping", Fixtures.TIME.minusSeconds(20)));
        assertEquals(Fixtures.TIME, store.get("shipping").emittedAt());
        assertNull(store.get("inventory"));
        assertNull(orders.get("1042"));
    }

    @Test
    void keyMismatchFailsProcessingWithoutWritingAnOrder() {
        assertThrows(RuntimeException.class, () -> checkout.pipeInput("9999", Fixtures.checkout(1042)));
        assertNull(orders.get("9999"));
        assertNull(orders.get("1042"));
    }

    @Test
    void heartbeatKeyMismatchFailsVisibly() {
        assertThrows(RuntimeException.class,
                () -> heartbeats.pipeInput("inventory", new SourceHeartbeat("shipping", Fixtures.TIME)));
    }

    @Test
    void tombstonesAreUnsupportedAndFailVisibly() {
        assertThrows(RuntimeException.class, () -> shipping.pipeInput("1042", (ShippingStatusChanged) null));
    }

    @Test
    void poisonJsonFailsDeserialization() {
        var raw = driver.createInputTopic("shipping-status", new StringSerializer(), new ByteArraySerializer());
        assertThrows(RuntimeException.class, () -> raw.pipeInput("1042", "{broken".getBytes(StandardCharsets.UTF_8)));
    }
}
