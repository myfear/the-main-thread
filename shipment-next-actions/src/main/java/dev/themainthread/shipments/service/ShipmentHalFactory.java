package dev.themainthread.shipments.service;

import dev.themainthread.shipments.model.Shipment;
import io.quarkus.hal.HalEntityWrapper;
import io.quarkus.hal.HalService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Link;

@ApplicationScoped
public class ShipmentHalFactory {

    private final HalService halService;
    private final ShipmentWorkflow workflow;

    public ShipmentHalFactory(HalService halService, ShipmentWorkflow workflow) {
        this.halService = halService;
        this.workflow = workflow;
    }

    public HalEntityWrapper<Shipment> wrap(Shipment shipment) {
        HalEntityWrapper<Shipment> wrapper = halService.toHalWrapper(shipment);

        for (String action : workflow.allowedActions(shipment)) {
            wrapper.addLinks(Link.fromPath("/shipments/%d/%s".formatted(shipment.id(), action))
                    .rel(action)
                    .build());
        }

        return wrapper;
    }
}
