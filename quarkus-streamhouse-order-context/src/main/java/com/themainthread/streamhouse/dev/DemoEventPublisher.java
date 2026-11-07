package com.themainthread.streamhouse.dev;

import java.time.Duration;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;

import com.themainthread.streamhouse.api.ApiError;
import com.themainthread.streamhouse.event.InventoryStatusChanged;
import com.themainthread.streamhouse.event.OrderPlaced;
import com.themainthread.streamhouse.event.ShippingStatusChanged;
import com.themainthread.streamhouse.event.SourceHeartbeat;

import io.quarkus.arc.profile.IfBuildProfile;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@IfBuildProfile("dev")
@ApplicationScoped
public class DemoEventPublisher {
    private final MutinyEmitter<OrderPlaced> checkout;
    private final MutinyEmitter<InventoryStatusChanged> inventory;
    private final MutinyEmitter<ShippingStatusChanged> shipping;
    private final MutinyEmitter<SourceHeartbeat> heartbeat;

    public DemoEventPublisher(@Channel("order-placed") MutinyEmitter<OrderPlaced> checkout,
            @Channel("inventory-status") MutinyEmitter<InventoryStatusChanged> inventory,
            @Channel("shipping-status") MutinyEmitter<ShippingStatusChanged> shipping,
            @Channel("source-heartbeats") MutinyEmitter<SourceHeartbeat> heartbeat) {
        this.checkout = checkout;
        this.inventory = inventory;
        this.shipping = shipping;
        this.heartbeat = heartbeat;
    }

    public void publish(OrderPlaced event) {
        send(checkout, Long.toString(event.orderId()), event);
    }

    public void publish(InventoryStatusChanged event) {
        send(inventory, Long.toString(event.orderId()), event);
    }

    public void publish(ShippingStatusChanged event) {
        send(shipping, Long.toString(event.orderId()), event);
    }

    public void publish(SourceHeartbeat event) {
        send(heartbeat, event.source(), event);
    }

    private <T> void send(MutinyEmitter<T> emitter, String key, T event) {
        var metadata = OutgoingKafkaRecordMetadata.<String>builder().withKey(key).build();
        try {
            emitter.sendMessage(Message.of(event).addMetadata(metadata)).await().atMost(Duration.ofSeconds(12));
        } catch (RuntimeException failure) {
            // A timed-out acknowledgement is uncertain: callers must retry the SAME event.
            throw new WebApplicationException(Response.status(503)
                    .entity(new ApiError("PUBLISH_UNCONFIRMED", "Broker acknowledgement was not confirmed; retry the same event")).build());
        }
    }
}
