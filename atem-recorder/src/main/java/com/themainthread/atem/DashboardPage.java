package com.themainthread.atem;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@Path("/")
public class DashboardPage {
    private final RecorderConfig config;

    DashboardPage(RecorderConfig config) {
        this.config = config;
    }

    @CheckedTemplate(basePath = "")
    static class Templates {
        static native TemplateInstance dashboard(String streamPath);

        static native TemplateInstance downloadError(String message);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        return Templates.dashboard(config.streamPath());
    }
}
