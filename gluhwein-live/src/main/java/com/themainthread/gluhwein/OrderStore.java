package com.themainthread.gluhwein;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;

@ApplicationScoped
public class OrderStore {
    private static final int MAX_ORDERS = 100;
    private final Map<UUID, Order> orders = new LinkedHashMap<>();
    private final Map<UUID, Set<MultiEmitter<? super Order>>> watchers = new LinkedHashMap<>();
    private final Set<MultiEmitter<? super List<Order>>> counters = new HashSet<>();
    private int nextNumber = 1;

    public synchronized Order create(String name, Order.Drink drink, int quantity) {
        if (orders.size() >= MAX_ORDERS) {
            throw new WebApplicationException("The demo has reached 100 orders. Restart the application to reset it.", 503);
        }
        Order order = new Order(UUID.randomUUID(), nextNumber++, name.strip(), drink, quantity,
                Order.Status.QUEUED, 1, Instant.now());
        orders.put(order.id(), order);
        publishBoard();
        return order;
    }

    public synchronized Order get(UUID id) {
        Order order = orders.get(id);
        if (order == null) {
            throw new NotFoundException("This ticket is no longer available. The demo may have restarted.");
        }
        return order;
    }

    public synchronized List<Order> list() {
        return List.copyOf(orders.values());
    }

    public synchronized Order advance(UUID id, long expectedRevision) {
        Order previous = get(id);
        if (previous.revision() != expectedRevision) {
            throw new WebApplicationException("This order changed in another counter. Check its current state and try again.", 409);
        }
        Order.Status next = switch (previous.status()) {
            case QUEUED -> Order.Status.PREPARING;
            case PREPARING -> Order.Status.READY;
            case READY -> throw new WebApplicationException("This order is already ready.", 409);
        };
        Order order = new Order(previous.id(), previous.number(), previous.name(), previous.drink(),
                previous.quantity(), next, previous.revision() + 1, Instant.now());
        orders.put(id, order);
        // Copy the set: a downstream cancellation can remove a watcher during emission.
        for (var emitter : List.copyOf(watchers.getOrDefault(id, Set.of()))) {
            emitter.emit(order);
        }
        publishBoard();
        return order;
    }

    public Multi<Order> watch(UUID id) {
        get(id); // Return 404 before a streaming response has started.
        return Multi.createFrom().emitter(emitter -> register(id, emitter), BackPressureStrategy.LATEST);
    }

    private synchronized void register(UUID id, MultiEmitter<? super Order> emitter) {
        var listeners = watchers.computeIfAbsent(id, ignored -> new HashSet<>());
        listeners.add(emitter);
        emitter.onTermination(() -> remove(id, emitter));
        // Registration and snapshot use the same lock as updates: no subscription gap.
        emitter.emit(get(id));
    }

    private synchronized void remove(UUID id, MultiEmitter<? super Order> emitter) {
        var listeners = watchers.get(id);
        if (listeners != null) {
            listeners.remove(emitter);
            if (listeners.isEmpty()) {
                watchers.remove(id);
            }
        }
    }

    public Multi<List<Order>> watchBoard() {
        return Multi.createFrom().<List<Order>>emitter(this::registerCounter, BackPressureStrategy.LATEST);
    }

    private synchronized void registerCounter(MultiEmitter<? super List<Order>> emitter) {
        counters.add(emitter);
        emitter.onTermination(() -> removeCounter(emitter));
        emitter.emit(list());
    }

    private synchronized void removeCounter(MultiEmitter<? super List<Order>> emitter) {
        counters.remove(emitter);
    }

    private void publishBoard() {
        List<Order> snapshot = list();
        for (var emitter : List.copyOf(counters)) {
            emitter.emit(snapshot);
        }
    }

    synchronized int subscriberCount() {
        return counters.size() + watchers.values().stream().mapToInt(Set::size).sum();
    }
}
