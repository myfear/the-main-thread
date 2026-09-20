package com.themainthread.mdc;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;

import org.jboss.logmanager.MDC;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

public class RequestIdFilter {

    static final String HEADER = "X-Request-ID";
    static final String MDC_KEY = "request.id";
    private static final String PROPERTY = RequestIdFilter.class.getName() + ".id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @ServerRequestFilter
    public void request(ContainerRequestContext request) {
        List<String> values = request.getHeaders().get(HEADER);
        String incoming = values != null && values.size() == 1 ? values.getFirst() : null;
        String id = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();

        request.setProperty(PROPERTY, id);
        MDC.put(MDC_KEY, id);
    }

    @ServerResponseFilter
    public void response(ContainerRequestContext request, ContainerResponseContext response) {
        try {
            Object id = request.getProperty(PROPERTY);
            if (id != null) {
                response.getHeaders().putSingle(HEADER, id);
            }
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
