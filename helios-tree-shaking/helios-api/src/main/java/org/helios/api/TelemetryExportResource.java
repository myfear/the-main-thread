package org.helios.api;

import java.io.IOException;
import java.io.StringWriter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jboss.logging.Logger;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/export")
public class TelemetryExportResource {

    private static final Logger LOG = Logger.getLogger(TelemetryExportResource.class);

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String exportSampleCsv() throws IOException {
        LOG.debug("Generating sample telemetry CSV");

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("deviceId", "lat", "lon", "timestamp")
                .build();

        StringWriter buffer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(buffer, format)) {
            printer.printRecord("sample-7", "52.5200", "13.4050", "2025-05-06T08:00:00Z");
        }
        return buffer.toString();
    }
}
