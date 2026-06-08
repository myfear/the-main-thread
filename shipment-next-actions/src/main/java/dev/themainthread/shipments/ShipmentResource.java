package dev.themainthread.shipments;

import java.net.URI;
import java.util.List;

import dev.themainthread.shipments.api.CreateShipmentRequest;
import dev.themainthread.shipments.model.Shipment;
import dev.themainthread.shipments.service.ShipmentHalFactory;
import dev.themainthread.shipments.service.ShipmentStore;
import dev.themainthread.shipments.service.ShipmentWorkflow;
import io.quarkus.hal.HalEntityWrapper;
import io.quarkus.resteasy.reactive.links.InjectRestLinks;
import io.quarkus.resteasy.reactive.links.RestLink;
import io.quarkus.resteasy.reactive.links.RestLinkType;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.common.util.RestMediaType;

@Path("/shipments")
@Produces(MediaType.APPLICATION_JSON)
public class ShipmentResource {

    private final ShipmentStore store;
    private final ShipmentWorkflow workflow;
    private final ShipmentHalFactory halFactory;

    public ShipmentResource(
            ShipmentStore store,
            ShipmentWorkflow workflow,
            ShipmentHalFactory halFactory) {
        this.store = store;
        this.workflow = workflow;
        this.halFactory = halFactory;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreateShipmentRequest request, @Context UriInfo uriInfo) {
        Shipment created = store.create(request);
        URI location = uriInfo.getAbsolutePathBuilder()
                .path(Long.toString(created.id()))
                .build();
        return Response.created(location)
                .entity(created)
                .build();
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON, RestMediaType.APPLICATION_HAL_JSON })
    @RestLink(rel = "list")
    @InjectRestLinks
    public List<Shipment> list() {
        return store.list();
    }

    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_JSON, RestMediaType.APPLICATION_HAL_JSON })
    @RestLink(rel = "self")
    @InjectRestLinks(RestLinkType.INSTANCE)
    public HalEntityWrapper<Shipment> get(@PathParam("id") long id) {
        return halFactory.wrap(store.get(id));
    }

    @PUT
    @Path("/{id}/pay")
    public Shipment pay(@PathParam("id") long id) {
        return store.update(workflow.pay(store.get(id)));
    }

    @PUT
    @Path("/{id}/pack")
    public Shipment pack(@PathParam("id") long id) {
        return store.update(workflow.pack(store.get(id)));
    }

    @PUT
    @Path("/{id}/ship")
    public Shipment ship(@PathParam("id") long id) {
        return store.update(workflow.ship(store.get(id)));
    }

    @PUT
    @Path("/{id}/deliver")
    public Shipment deliver(@PathParam("id") long id) {
        return store.update(workflow.deliver(store.get(id)));
    }

    @PUT
    @Path("/{id}/cancel")
    public Shipment cancel(@PathParam("id") long id) {
        return store.update(workflow.cancel(store.get(id)));
    }
}
