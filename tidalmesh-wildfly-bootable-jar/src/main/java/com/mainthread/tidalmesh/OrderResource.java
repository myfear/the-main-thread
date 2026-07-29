package com.mainthread.tidalmesh;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

@Path("/orders")
@RequestScoped
public class OrderResource {

    private final OrderSession orderSession;

    protected OrderResource() {
        this.orderSession = null;
    }

    @Inject
    public OrderResource(OrderSession orderSession) {
        this.orderSession = orderSession;
    }

    @POST
    @Path("/{orderId}/check-ins")
    @Produces(APPLICATION_JSON)
    public Response recordCheckIn(@PathParam("orderId") String orderId, @Context HttpServletRequest request) {
        int checkIns = orderSession.record(orderId);
        String nodeName = System.getProperty("jboss.node.name", "local");
        String transactionNodeId = System.getProperty("jboss.tx.node.id", "local");
        String body = Json.createObjectBuilder()
                .add("orderId", orderId)
                .add("checkIns", checkIns)
                .add("nodeName", nodeName)
                .add("transactionNodeId", transactionNodeId)
                .add("sessionId", request.getSession().getId())
                .build()
                .toString();

        return Response.ok(body, APPLICATION_JSON).build();
    }
}
