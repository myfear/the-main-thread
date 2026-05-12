package dev.deskflow;

import dev.deskflow.model.SupportTicket;
import dev.deskflow.model.TriageResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/tickets")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TicketResource {

    private final TicketOrchestrator orchestrator;

    @Inject
    public TicketResource(TicketOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @POST
    public TriageResult submitTicket(SupportTicket ticket) {
        SupportTicket enriched =
                ticket.id() == null || ticket.id().isBlank()
                        ? new SupportTicket(
                                UUID.randomUUID().toString(),
                                ticket.summary(),
                                ticket.details(),
                                ticket.reportedBy())
                        : ticket;
        return orchestrator.process(enriched);
    }
}
