package com.themainthread.diagrams;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@Path("/")
public class DiagramPage {
    private final DiagramCatalog catalog;

    public DiagramPage(DiagramCatalog catalog) {
        this.catalog = catalog;
    }

    @CheckedTemplate(basePath = "")
    public static class Templates {
        public static native TemplateInstance diagrams(String source);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        return Templates.diagrams(catalog.source());
    }
}
