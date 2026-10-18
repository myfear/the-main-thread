package com.themainthread.gluhwein;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;

import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestStreamElementType;

import io.smallrye.mutiny.Multi;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {
    private final OrderStore store;
    private final Sse sse;

    public OrderResource(OrderStore store, Sse sse) {
        this.store = store;
        this.sse = sse;
    }

    public record PlaceOrder(@NotBlank @Size(max = 32) String name,
            @NotNull Order.Drink drink, @Min(1) @Max(6) int quantity) {
    }

    public record AdvanceOrder(@Min(1) long revision) {
    }

    @POST
    public Response create(@NotNull @Valid PlaceOrder request) {
        Order order = store.create(request.name(), request.drink(), request.quantity());
        return Response.created(URI.create("/api/orders/" + order.id())).entity(order).build();
    }

    @GET
    public List<Order> list() {
        return store.list();
    }

    @GET
    @Path("/{id}")
    public Order get(@RestPath UUID id) {
        return store.get(id);
    }

    @POST
    @Path("/{id}/advance")
    public Order advance(@RestPath UUID id, @NotNull @Valid AdvanceOrder request) {
        return store.advance(id, request.revision());
    }

    @GET
    @Path("/{id}/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<OutboundSseEvent> events(@RestPath UUID id) {
        Multi<OutboundSseEvent> changes = store.watch(id).map(order -> sse.newEventBuilder()
                .name("order").id(Long.toString(order.revision())).reconnectDelay(2000)
                .mediaType(MediaType.APPLICATION_JSON_TYPE).data(Order.class, order).build());
        return withHeartbeats(changes);
    }

    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<OutboundSseEvent> board() {
        return withHeartbeats(store.watchBoard().map(orders -> sse.newEventBuilder()
                .name("board").reconnectDelay(2000).mediaType(MediaType.APPLICATION_JSON_TYPE)
                .data(orders).build()));
    }

    private Multi<OutboundSseEvent> withHeartbeats(Multi<OutboundSseEvent> changes) {
        Multi<OutboundSseEvent> heartbeats = Multi.createFrom().ticks().every(Duration.ofSeconds(15))
                .onOverflow().drop()
                .map(tick -> sse.newEventBuilder().comment("keep-alive").build());
        return Multi.createBy().merging().streams(changes, heartbeats);
    }
}
