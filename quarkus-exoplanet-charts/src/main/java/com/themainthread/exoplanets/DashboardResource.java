package com.themainthread.exoplanets;

import java.time.Year;
import java.util.Map;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import com.themainthread.exoplanets.CatalogueService.CatalogueUnavailableException;
import com.themainthread.exoplanets.CatalogueService.Source;

@Path("/api/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {
    private static final Logger LOG = Logger.getLogger(DashboardResource.class);
    private final CatalogueService catalogues;
    private final ChartService charts;

    public DashboardResource(CatalogueService catalogues, ChartService charts) {
        this.catalogues = catalogues;
        this.charts = charts;
    }

    @GET
    public Response dashboard(@QueryParam("source") @DefaultValue("snapshot") String source,
            @QueryParam("from") @DefaultValue("1992") String fromText,
            @QueryParam("to") @DefaultValue("2025") String toText) {
        int from;
        int to;
        Source selectedSource;
        try {
            from = Integer.parseInt(fromText);
            to = Integer.parseInt(toText);
            selectedSource = Source.valueOf(source);
        } catch (IllegalArgumentException exception) {
            return error(400, "Use integer years and source=snapshot or source=live.");
        }
        if (from < 1992 || to > Year.now().getValue() || from > to) {
            return error(400, "Years must be ordered and between 1992 and the current year.");
        }
        try {
            return Response.ok(charts.build(catalogues.load(selectedSource), source, from, to))
                    .header("Cache-Control", "no-store").build();
        } catch (CatalogueUnavailableException exception) {
            LOG.warn("Catalogue request failed", exception);
            return error(503, "Catalogue unavailable. Retry later or choose the bundled snapshot.");
        }
    }

    private static Response error(int status, String message) {
        return Response.status(status).entity(Map.of("error", message)).header("Cache-Control", "no-store").build();
    }
}
