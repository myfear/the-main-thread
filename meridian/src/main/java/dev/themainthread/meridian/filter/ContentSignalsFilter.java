package dev.themainthread.meridian.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class ContentSignalsFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        // Use getPath(true): RESTEasy Reactive (Quarkus REST) rejects getPath(false)
        // with "We do not support non-decoded parameters".
        String path = requestContext.getUriInfo().getPath(true);

        if (path.startsWith("api/v1/articles") || path.startsWith("/api/v1/articles")) {
            responseContext.getHeaders().putSingle("Content-Signal", "search=yes, ai-input=yes, ai-train=no");
            return;
        }

        if (path.startsWith(".well-known")
                || path.startsWith("/.well-known")
                || path.equals("robots.txt")
                || path.equals("/robots.txt")
                || path.equals("llms.txt")
                || path.equals("/llms.txt")) {
            responseContext.getHeaders().putSingle("Content-Signal", "search=yes, ai-input=yes, ai-train=yes");
        }
    }
}
