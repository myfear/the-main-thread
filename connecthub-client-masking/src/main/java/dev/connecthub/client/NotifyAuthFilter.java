package dev.connecthub.client;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

import dev.connecthub.DemoTokens;

public class NotifyAuthFilter implements ClientRequestFilter {

    @Override
    public void filter(ClientRequestContext requestContext) {
        requestContext.getHeaders().putSingle(HttpHeaders.COOKIE, "session=" + DemoTokens.SESSION);
        requestContext.getHeaders().putSingle("X-ConnectHub-Signature", DemoTokens.SIGNATURE);
    }
}
