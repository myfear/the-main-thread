package com.themainthread.checkout;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;

final class KafkaProbe {

    private KafkaProbe() {
    }

    static List<ConsumerRecord<String, String>> records(String bootstrap, UUID eventId) throws Exception {
        ObjectMapper json = new ObjectMapper();
        try (KafkaConsumer<String, String> observer = new KafkaConsumer<>(Map.of(
                "bootstrap.servers", bootstrap,
                "group.id", "outbox-observer-" + UUID.randomUUID(),
                "enable.auto.commit", false,
                "key.deserializer", StringDeserializer.class.getName(),
                "value.deserializer", StringDeserializer.class.getName()))) {
            var partitions = observer.partitionsFor("order-placed", Duration.ofSeconds(10)).stream()
                    .map(p -> new TopicPartition(p.topic(), p.partition())).toList();
            observer.assign(partitions);
            observer.seekToBeginning(partitions);
            var ends = observer.endOffsets(partitions, Duration.ofSeconds(10));
            List<ConsumerRecord<String, String>> matches = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (partitions.stream().anyMatch(p -> observer.position(p) < ends.get(p))) {
                assertTrue(System.nanoTime() < deadline, "Observer did not reach captured end offsets");
                for (var record : observer.poll(Duration.ofMillis(100))) {
                    if (record.offset() < ends.get(new TopicPartition(record.topic(), record.partition()))
                            && eventId.toString().equals(json.readTree(record.value()).path("eventId").asText())) {
                        matches.add(record);
                    }
                }
            }
            try (Admin admin = Admin.create(Map.of("bootstrap.servers", bootstrap))) {
                Awaitility.await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
                    var offsets = admin.listConsumerGroupOffsets("checkout-fulfillment")
                            .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
                    for (var record : matches) {
                        var offset = offsets.get(new TopicPartition(record.topic(), record.partition()));
                        assertNotNull(offset);
                        assertTrue(offset.offset() > record.offset(), "Fulfillment has not committed this delivery");
                    }
                });
            }
            return matches;
        }
    }
}
