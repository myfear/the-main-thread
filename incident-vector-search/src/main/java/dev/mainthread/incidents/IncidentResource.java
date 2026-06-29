package dev.mainthread.incidents;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/incidents")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class IncidentResource {

    private final IncidentArchive archive;

    @Inject
    IncidentResource(IncidentArchive archive) {
        this.archive = archive;
    }

    @POST
    public IndexedIncident index(@Valid IncidentInput incident) {
        return archive.index(incident);
    }

    @POST
    @Path("/search")
    public SearchResponse search(@Valid SimilarIncidentRequest request) {
        return archive.search(request);
    }

    @POST
    @Path("/seed")
    @Consumes(MediaType.WILDCARD)
    public SeedResponse seed() {
        return archive.seed(IncidentFixtures.examples());
    }
}
