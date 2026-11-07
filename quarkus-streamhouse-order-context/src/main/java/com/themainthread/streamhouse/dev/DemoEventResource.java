package com.themainthread.streamhouse.dev;

import com.themainthread.streamhouse.event.EventValidation;
import com.themainthread.streamhouse.event.InventoryStatusChanged;
import com.themainthread.streamhouse.event.OrderPlaced;
import com.themainthread.streamhouse.event.ShippingStatusChanged;
import com.themainthread.streamhouse.event.SourceHeartbeat;
import com.themainthread.streamhouse.projection.LivenessEvaluation;

import io.quarkus.arc.profile.IfBuildProfile;
import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@IfBuildProfile("dev")
@Path("/_demo")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class DemoEventResource {
    private final DemoEventPublisher publisher;
    private final LivenessEvaluation liveness;

    public DemoEventResource(DemoEventPublisher publisher, LivenessEvaluation liveness) {
        this.publisher = publisher;
        this.liveness = liveness;
    }

    @POST
    @Path("/order-placed")
    public void checkout(OrderPlaced event) {
        publisher.publish(EventValidation.required(event, "event"));
    }

    @POST
    @Path("/inventory")
    public void inventory(InventoryStatusChanged event) {
        publisher.publish(EventValidation.required(event, "event"));
    }

    @POST
    @Path("/shipping")
    public void shipping(ShippingStatusChanged event) {
        publisher.publish(EventValidation.required(event, "event"));
    }

    @POST
    @Path("/heartbeat")
    public void heartbeat(SourceHeartbeat event) {
        if (!liveness.acceptable(EventValidation.required(event, "event"))) {
            throw new IllegalArgumentException("Heartbeat exceeds the future clock-skew allowance");
        }
        publisher.publish(event);
    }
}
