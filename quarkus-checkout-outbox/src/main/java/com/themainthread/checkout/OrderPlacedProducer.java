package com.themainthread.checkout;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;

import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderPlacedProducer {

    private final MutinyEmitter<OrderPlaced> emitter;

    OrderPlacedProducer(@Channel("order-placed") MutinyEmitter<OrderPlaced> emitter) {
        this.emitter = emitter;
    }

    public void send(OrderPlaced event) {
        emitter.sendMessageAndAwait(Message.of(event).addMetadata(metadata(event)));
    }

    private static OutgoingKafkaRecordMetadata<String> metadata(OrderPlaced event) {
        return OutgoingKafkaRecordMetadata.<String>builder()
                .withKey(Long.toString(event.orderId()))
                .withHeaders(new RecordHeaders()
                        .add("id", event.eventId().toString().getBytes(StandardCharsets.UTF_8)))
                .build();
    }
}
