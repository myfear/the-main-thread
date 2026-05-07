package org.helios.analytics;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jboss.logging.Logger;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/import")
public class TelemetryImportResource {

    private static final Logger LOG = Logger.getLogger(TelemetryImportResource.class);

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importCsv(String csvPayload) throws IOException {
        LOG.debug("Importing telemetry CSV payload");

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();

        try (CSVParser parser = format.parse(new StringReader(csvPayload))) {

            List<TelemetryRecord> records = parser.stream()
                    .map(this::toRecord)
                    .toList();

            return Response.ok(Map.of("imported", records.size())).build();
        }
    }

    private TelemetryRecord toRecord(CSVRecord row) {
        return new TelemetryRecord(
                row.get("deviceId"),
                Double.parseDouble(row.get("lat")),
                Double.parseDouble(row.get("lon")),
                Instant.parse(row.get("timestamp")));
    }
}