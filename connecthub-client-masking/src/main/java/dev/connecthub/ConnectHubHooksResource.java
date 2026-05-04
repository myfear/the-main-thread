package dev.connecthub;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import dev.connecthub.client.NotifyApiClient;
import dev.connecthub.client.PaymentsApiClient;

@Path("/connect/hooks")
public class ConnectHubHooksResource {

    private static final Logger LOG = Logger.getLogger(ConnectHubHooksResource.class);

    @Inject
    @RestClient
    PaymentsApiClient paymentsApiClient;

    @Inject
    @RestClient
    NotifyApiClient notifyApiClient;

    @GET
    @Path("/demo")
    @Produces(MediaType.TEXT_PLAIN)
    public String runDemo() {
        String p = paymentsApiClient.ping();
        String n = notifyApiClient.ping();
        LOG.infof("ConnectHub demo finished: payments=%s notify=%s", p, n);
        return "ok";
    }

    @GET
    @Path("/payments-only")
    @Produces(MediaType.TEXT_PLAIN)
    public String paymentsOnly() {
        return paymentsApiClient.ping();
    }

    @GET
    @Path("/notify-only")
    @Produces(MediaType.TEXT_PLAIN)
    public String notifyOnly() {
        return notifyApiClient.ping();
    }
}
