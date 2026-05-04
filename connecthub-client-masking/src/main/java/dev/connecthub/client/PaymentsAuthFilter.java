package dev.connecthub.client;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

import dev.connecthub.DemoTokens;

public class PaymentsAuthFilter implements ClientRequestFilter {

    @Override
    public void filter(ClientRequestContext requestContext) {
        requestContext.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, "Bearer " + DemoTokens.BEARER);
        requestContext.getHeaders().putSingle("X-ConnectHub-Signature", DemoTokens.SIGNATURE);
    }
}
