package com.themainthread.checkout;

import java.time.Instant;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OrderService {

    private final OrderRepository orderRepository;
    private final FulfillmentGateway fulfillmentGateway;

    public OrderService(OrderRepository orderRepository, FulfillmentGateway fulfillmentGateway) {
        this.orderRepository = orderRepository;
        this.fulfillmentGateway = fulfillmentGateway;
    }

    @Transactional
    public OrderView create(CheckoutRequest request) {
        String fulfillmentReference = fulfillmentGateway.dispatch(request);

        PurchaseOrder order = new PurchaseOrder();
        order.sku = request.sku();
        order.quantity = request.quantity();
        order.status = OrderStatus.ACCEPTED;
        order.fulfillmentReference = fulfillmentReference;
        order.createdAt = Instant.now();

        orderRepository.persistAndFlush(order);
        return OrderView.from(order);
    }

    public Optional<OrderView> find(long id) {
        return orderRepository.findByIdOptional(id).map(OrderView::from);
    }

    public CheckoutStats stats() {
        return new CheckoutStats(
                orderRepository.count(),
                fulfillmentGateway.dispatchCount(),
                fulfillmentGateway.processingCount());
    }
}
