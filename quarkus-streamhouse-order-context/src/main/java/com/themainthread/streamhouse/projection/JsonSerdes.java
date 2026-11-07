package com.themainthread.streamhouse.projection;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.themainthread.streamhouse.event.InventoryStatusChanged;
import com.themainthread.streamhouse.event.OrderEvent;
import com.themainthread.streamhouse.event.OrderPlaced;
import com.themainthread.streamhouse.event.ShippingStatusChanged;
import com.themainthread.streamhouse.event.SourceHeartbeat;

import io.quarkus.kafka.client.serialization.ObjectMapperSerde;

public final class JsonSerdes {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);

    private JsonSerdes() {
    }

    public static ObjectMapperSerde<OrderPlaced> checkout() {
        return new ObjectMapperSerde<>(OrderPlaced.class, MAPPER);
    }

    public static ObjectMapperSerde<InventoryStatusChanged> inventory() {
        return new ObjectMapperSerde<>(InventoryStatusChanged.class, MAPPER);
    }

    public static ObjectMapperSerde<ShippingStatusChanged> shipping() {
        return new ObjectMapperSerde<>(ShippingStatusChanged.class, MAPPER);
    }

    public static ObjectMapperSerde<SourceHeartbeat> heartbeat() {
        return new ObjectMapperSerde<>(SourceHeartbeat.class, MAPPER);
    }

    public static ObjectMapperSerde<OrderEvent> event() {
        return new ObjectMapperSerde<>(OrderEvent.class, MAPPER);
    }

    public static ObjectMapperSerde<OrderState> state() {
        return new ObjectMapperSerde<>(OrderState.class, MAPPER);
    }
}
