package com.themainthread.checkout;

import java.net.URI;
import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.smallrye.common.annotation.Blocking;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class CheckoutResource {

    private final CheckoutService checkout;
    private final DualWriteCheckoutService dualWrite;

    CheckoutResource(CheckoutService checkout, DualWriteCheckoutService dualWrite) {
        this.checkout = checkout;
        this.dualWrite = dualWrite;
    }

    @POST
    @Path("/orders")
    @Consumes(MediaType.APPLICATION_JSON)
    @Blocking
    public Response place(@Valid CheckoutRequest request, @HeaderParam("X-Crash-After") String crashAfter) {
        PurchaseOrder order = checkout.place(request, CrashPoint.fromHeader(crashAfter));
        return created(order);
    }

    @POST
    @Path("/orders/dual-write")
    @Consumes(MediaType.APPLICATION_JSON)
    @Blocking
    public Response dualWrite(@Valid CheckoutRequest request, @HeaderParam("X-Crash-After") String crashAfter) {
        PurchaseOrder order = dualWrite.place(request, CrashPoint.fromHeader(crashAfter));
        return created(order);
    }

    @GET
    @Path("/orders/{id}")
    @Blocking
    @Transactional
    public OrderView getOrder(@PathParam("id") long id) {
        PurchaseOrder order = PurchaseOrder.findById(id);
        if (order == null) {
            throw new NotFoundException();
        }
        return OrderView.from(order);
    }

    @GET
    @Path("/outbox")
    @Blocking
    @Transactional
    public List<OutboxView> outbox() {
        return OutboxEvent.<OutboxEvent>listAll().stream().map(OutboxView::from).toList();
    }

    @GET
    @Path("/fulfillments")
    @Blocking
    @Transactional
    public List<FulfillmentView> fulfillments() {
        return Fulfillment.<Fulfillment>listAll().stream().map(FulfillmentView::from).toList();
    }

    @GET
    @Path("/fulfillments/order/{orderId}")
    @Blocking
    @Transactional
    public FulfillmentView fulfillmentForOrder(@PathParam("orderId") long orderId) {
        Fulfillment fulfillment = Fulfillment.findByOrderId(orderId);
        if (fulfillment == null) {
            throw new NotFoundException();
        }
        return FulfillmentView.from(fulfillment);
    }

    private static Response created(PurchaseOrder order) {
        return Response.created(URI.create("/orders/" + order.id))
                .entity(OrderView.from(order))
                .build();
    }
}
