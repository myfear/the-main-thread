package com.themainthread.streamhouse;

import static com.themainthread.streamhouse.event.InventoryStatusChanged.Status.RESERVED;
import static com.themainthread.streamhouse.event.ShippingStatusChanged.Status.DELAYED;
import static com.themainthread.streamhouse.event.ShippingStatusChanged.Status.PACKED;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.themainthread.streamhouse.event.SourceHeartbeat;
import com.themainthread.streamhouse.projection.JsonSerdes;

public abstract class OrderContextContract {
    private KafkaProducer<String, byte[]> producer;

    @BeforeEach
    void producer() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class));
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10000);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        producer = new KafkaProducer<>(properties);
        await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(250)).untilAsserted(() ->
                given().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP")));
    }

    @AfterEach
    void close() {
        if (producer != null) {
            producer.close(Duration.ofSeconds(5));
        }
    }

    @Test
    void realKafkaProducesPartialCompleteAndConflictedViews() throws Exception {
        long id = ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
        String key = Long.toString(id);
        String path = "/orders/" + id + "/context";
        send("order-placed", key, Fixtures.checkout(id), JsonSerdes.checkout());
        eventually(() -> given().get(path).then().statusCode(200)
                .body("checkout.status", equalTo("PLACED"), "complete", equalTo(false),
                        "missingSources", hasItems("inventory", "shipping"), "catchUpVerified", equalTo(false)));
        send("shipping-status", key, Fixtures.shipping(id, 1, PACKED), JsonSerdes.shipping());
        send("inventory-status", key, Fixtures.inventory(id, 1, RESERVED), JsonSerdes.inventory());
        send("shipping-status", key, Fixtures.shipping(id, 2, DELAYED), JsonSerdes.shipping());
        eventually(() -> given().get(path).then().statusCode(200)
                .body("complete", equalTo(true), "shipping.status", equalTo("DELAYED"), "shipping.revision", equalTo(2),
                        "missingSources", hasSize(0), "processorState", equalTo("RUNNING")));
        send("shipping-status", key, Fixtures.shipping(id, 1, PACKED), JsonSerdes.shipping());
        send("shipping-status", key, Fixtures.shipping(id, 2, DELAYED), JsonSerdes.shipping());
        // A later contradiction on the SAME topic/partition proves the prior inputs were consumed.
        send("shipping-status", key, Fixtures.shipping(id, 2, PACKED), JsonSerdes.shipping());
        eventually(() -> given().get(path).then().statusCode(200)
                .body("conflict", equalTo(true), "complete", equalTo(false), "shipping.status", equalTo("DELAYED"),
                        "shipping.revision", equalTo(2), "missingSources", hasSize(0)));
    }

    @Test
    void shippingOnlyIsAnIncomplete200AndUnseenOrderIs404() throws Exception {
        long id = ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
        given().get("/orders/" + id + "/context").then().statusCode(404).body("code", equalTo("ORDER_NOT_FOUND"));
        send("shipping-status", Long.toString(id), Fixtures.shipping(id, 2, DELAYED), JsonSerdes.shipping());
        eventually(() -> given().get("/orders/" + id + "/context").then().statusCode(200)
                .body("complete", equalTo(false), "missingSources", hasItems("checkout", "inventory")));
    }

    @Test
    void heartbeatsDescribeReportingWithoutPromisingCatchUp() throws Exception {
        long id = ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
        send("order-placed", Long.toString(id), Fixtures.checkout(id), JsonSerdes.checkout());
        for (String source : SourceHeartbeat.SOURCES) {
            send("source-heartbeats", source, new SourceHeartbeat(source, Instant.now()), JsonSerdes.heartbeat());
        }
        eventually(() -> given().get("/orders/" + id + "/context").then().statusCode(200)
                .body("sourceSignals.checkout", equalTo("RECENT"), "sourceSignals.inventory", equalTo("RECENT"),
                        "sourceSignals.shipping", equalTo("RECENT"), "catchUpVerified", equalTo(false), "complete", equalTo(false)));
    }

    @Test
    void demoPublishingRoutesAreUnavailable() {
        for (String route : new String[] {"order-placed", "inventory", "shipping", "heartbeat"}) {
            given().contentType("application/json").body("{}").post("/_demo/" + route).then().statusCode(404);
        }
    }

    private <T> void send(String topic, String key, T value, Serde<T> serde) throws Exception {
        producer.send(new ProducerRecord<>(topic, key, serde.serializer().serialize(topic, value))).get(10, TimeUnit.SECONDS);
    }

    private void eventually(org.awaitility.core.ThrowingRunnable assertion) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(assertion);
    }
}
