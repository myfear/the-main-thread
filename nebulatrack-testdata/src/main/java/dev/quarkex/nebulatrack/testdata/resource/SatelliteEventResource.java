package dev.quarkex.nebulatrack.testdata.resource;

import dev.quarkex.nebulatrack.testdata.model.SatelliteEvent;
import dev.quarkex.nebulatrack.testdata.model.ValidationResult;
import dev.quarkex.nebulatrack.testdata.service.SatelliteEventService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/events")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SatelliteEventResource {

    private final SatelliteEventService service;

    public SatelliteEventResource(SatelliteEventService service) {
        this.service = service;
    }

    @POST
    public Response ingest(SatelliteEvent event) {
        ValidationResult result = service.validate(event);
        if (!result.valid()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(result)
                    .build();
        }
        return Response.accepted(event).build();
    }
}
