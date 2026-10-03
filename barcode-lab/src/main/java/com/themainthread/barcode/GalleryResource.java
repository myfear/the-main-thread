package com.themainthread.barcode;

import java.util.Arrays;
import java.util.List;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public class GalleryResource {
    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance gallery(List<BarcodeType> formats);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        return gallery();
    }

    @GET
    @Path("gallery")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance gallery() {
        return Templates.gallery(Arrays.asList(BarcodeType.values()));
    }
}
