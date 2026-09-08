package com.themainthread.returns;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@Path("/")
public class OrderPage {
    private final ReturnService returns;

    public OrderPage(ReturnService returns) {
        this.returns = returns;
    }

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance order(ReturnService.Order order);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        return Templates.order(returns.order("ORD-1042"));
    }
}
