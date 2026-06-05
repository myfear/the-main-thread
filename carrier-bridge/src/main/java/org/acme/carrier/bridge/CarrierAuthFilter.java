package org.acme.carrier.bridge;

import io.quarkus.arc.Unremovable;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

@Unremovable
public class CarrierAuthFilter implements ClientRequestFilter {

    static final String DEMO_BEARER_TOKEN = "carrier-demo-bearer-token";
    static final String DEMO_API_KEY = "carrier-demo-api-key";

    @Override
    public void filter(ClientRequestContext requestContext) {
        requestContext.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, "Bearer " + DEMO_BEARER_TOKEN);
        requestContext.getHeaders().putSingle("X-Carrier-Key", DEMO_API_KEY);
    }
}
