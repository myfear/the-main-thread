package com.themainthread.streamhouse;

import static com.themainthread.streamhouse.event.InventoryStatusChanged.Status.RESERVED;
import static com.themainthread.streamhouse.event.ShippingStatusChanged.Status.DELAYED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.common.serialization.Serde;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.themainthread.streamhouse.event.OrderEvent;
import com.themainthread.streamhouse.event.SourceHeartbeat;
import com.themainthread.streamhouse.projection.JsonSerdes;
import com.themainthread.streamhouse.projection.OrderReducer;
import com.themainthread.streamhouse.projection.OrderState;

class JsonSerdesTest {
    private static final String ORIGINAL = """
            {"eventId":"74c6c857-4dd0-4e05-bdc0-577ba374f7d2","orderId":1042,
             "sku":"keyboard-1","quantity":1,"placedAt":"2026-10-27T13:10:00Z"}
            """;

    @Test
    void originalOutboxContractNeedsNoAdditionalFields() throws Exception {
        assertEquals(Fixtures.checkout(1042), JsonSerdes.checkout().deserializer()
                .deserialize("order-placed", ORIGINAL.getBytes(StandardCharsets.UTF_8)));
        String json = new String(JsonSerdes.checkout().serializer().serialize("order-placed", Fixtures.checkout(1042)),
                StandardCharsets.UTF_8);
        assertTrue(json.contains("2026-10-27T13:10:00Z"));
        assertEquals(5, new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).size());
    }

    @Test
    void everyEventAndChangelogValueRoundTrips() {
        roundTrip(JsonSerdes.checkout(), Fixtures.checkout(1042));
        roundTrip(JsonSerdes.inventory(), Fixtures.inventory(1042, 1, RESERVED));
        roundTrip(JsonSerdes.shipping(), Fixtures.shipping(1042, 2, DELAYED));
        roundTrip(JsonSerdes.heartbeat(), new SourceHeartbeat("shipping", Fixtures.TIME));
        var event = OrderEvent.from("1042", Fixtures.checkout(1042));
        roundTrip(JsonSerdes.event(), event);
        roundTrip(JsonSerdes.event(), OrderEvent.from("1042", Fixtures.inventory(1042, 1, RESERVED)));
        roundTrip(JsonSerdes.event(), OrderEvent.from("1042", Fixtures.shipping(1042, 2, DELAYED)));
        var state = OrderReducer.apply(OrderState.empty(), event);
        state = OrderReducer.apply(state, OrderEvent.from("1042", Fixtures.inventory(1042, 1, RESERVED)));
        state = OrderReducer.apply(state, OrderEvent.from("1042", Fixtures.shipping(1042, 2, DELAYED)));
        roundTrip(JsonSerdes.state(), state.conflicted());
        roundTrip(JsonSerdes.state(), OrderState.empty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0", "-1", "1.5"})
    void invalidPrimitiveValuesAreRejected(String quantity) {
        String json = ORIGINAL.replace("\"quantity\":1", "\"quantity\":" + quantity);
        assertThrows(RuntimeException.class,
                () -> JsonSerdes.checkout().deserializer().deserialize("order-placed", json.getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"FLYING\"", "0"})
    void nullUnknownAndNumericEnumsAreRejected(String status) {
        String json = """
                {"eventId":"0d40b6f3-6a0c-4cdd-91b8-a81ab039609b","orderId":1042,
                 "revision":1,"status":%s,"occurredAt":"2026-10-27T13:15:00Z"}
                """.formatted(status);
        assertThrows(RuntimeException.class,
                () -> JsonSerdes.shipping().deserializer().deserialize("shipping-status", json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void missingIdsInvalidRevisionUnknownFieldsAndSourceAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Fixtures.shipping(1042, 0, DELAYED));
        assertThrows(IllegalArgumentException.class, () -> Fixtures.inventory(1042, -1, RESERVED));
        assertThrows(IllegalArgumentException.class, () -> new SourceHeartbeat("other", Fixtures.TIME));
        assertThrows(IllegalArgumentException.class, () -> new SourceHeartbeat("shipping", null));
        for (String json : new String[] {ORIGINAL.replace("\"quantity\":1,", ""),
                ORIGINAL.replace("74c6c857-4dd0-4e05-bdc0-577ba374f7d2", "invalid"),
                ORIGINAL.replace("\"sku\":\"keyboard-1\"", "\"sku\":null"),
                ORIGINAL.replace("\"sku\":\"keyboard-1\"", "\"sku\":\" \""),
                ORIGINAL.replace("\"orderId\":1042", "\"orderId\":0"),
                ORIGINAL.replace("\"quantity\":1", "\"quantity\":1,\"payment\":\"PAID\"")}) {
            assertThrows(RuntimeException.class,
                    () -> JsonSerdes.checkout().deserializer().deserialize("order-placed", json.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private <T> void roundTrip(Serde<T> serde, T value) {
        assertEquals(value, serde.deserializer().deserialize("test", serde.serializer().serialize("test", value)));
    }
}
