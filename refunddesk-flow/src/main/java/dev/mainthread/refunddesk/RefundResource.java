package dev.mainthread.refunddesk;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.serverlessworkflow.impl.WorkflowInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@Path("/refunds")
@ApplicationScoped
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class RefundResource {

    private static final JsonFormat CE_JSON = (JsonFormat) EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);

    @Inject
    RefundWorkflow workflow;

    @Inject
    DecisionStore decisions;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Channel("flow-in-outgoing")
    Emitter<byte[]> flowIn;

    @POST
    public Response submit(RefundRequest request) {
        WorkflowInstance instance = workflow.instance(request);
        instance.start();

        return Response.accepted(new SubmitRefundResponse(request.refundId(), instance.id())).build();
    }

    @GET
    @Path("/{refundId}")
    public Response result(@PathParam("refundId") String refundId) {
        return decisions.find(refundId)
                .map(result -> Response.ok(result).build())
                .orElseGet(() -> Response.status(Status.NOT_FOUND).build());
    }

    @POST
    @Path("/{refundId}/review/{instanceId}")
    public Response review(@PathParam("refundId") String refundId,
            @PathParam("instanceId") String instanceId,
            ReviewDecision review) throws JsonProcessingException {

        if (!refundId.equals(review.refundId())) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(Map.of("error", "path refundId and review refundId must match"))
                    .build();
        }

        byte[] body = objectMapper.writeValueAsBytes(review);
        byte[] event = CE_JSON.serialize(CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("urn:refunddesk:review-api"))
                .withType("refund.review.completed")
                .withDataContentType(APPLICATION_JSON)
                .withExtension("flowinstanceid", instanceId)
                .withData(body)
                .build());

        flowIn.send(event);
        return Response.accepted().build();
    }
}
