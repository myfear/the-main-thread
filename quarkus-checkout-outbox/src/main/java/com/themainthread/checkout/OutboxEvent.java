package com.themainthread.checkout;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent extends PanacheEntityBase {

    public static final String AGGREGATE_TYPE = "Order";
    public static final String EVENT_TYPE = "OrderPlaced";

    @Id
    public UUID id;

    @Column(nullable = false)
    public String aggregatetype;

    @Column(nullable = false)
    public String aggregateid;

    @Column(nullable = false)
    public String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public String payload;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "published_at")
    public Instant publishedAt;

    public static OutboxEvent enqueue(OrderPlaced event, ObjectMapper json) {
        OutboxEvent row = new OutboxEvent();
        row.id = event.eventId();
        row.aggregatetype = AGGREGATE_TYPE;
        row.aggregateid = Long.toString(event.orderId());
        row.type = EVENT_TYPE;
        row.payload = writeJson(json, event);
        row.createdAt = Instant.now();
        row.persist();
        return row;
    }

    public static List<OutboxEvent> unpublished(int limit) {
        return find("publishedAt is null order by createdAt").page(0, limit).list();
    }

    public static long unpublishedCount() {
        return count("publishedAt is null");
    }

    private static String writeJson(ObjectMapper json, OrderPlaced event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize OrderPlaced", e);
        }
    }
}
