package com.themainthread.filterlab;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/api")
public class FilterResource {

    private static final int MAX_PIXELS = 24_000_000;
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    private final FilterCatalog catalog;
    private final ImageFilterService filters;
    private final SampleImage sample;
    private final ObjectMapper objectMapper;

    public FilterResource(FilterCatalog catalog, ImageFilterService filters, SampleImage sample,
            ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.filters = filters;
        this.sample = sample;
        this.objectMapper = objectMapper;
    }

    @GET
    @Path("/filters")
    @Produces(MediaType.APPLICATION_JSON)
    public List<FilterSpec> filters() {
        return catalog.all();
    }

    @GET
    @Path("/sample.png")
    @Produces("image/png")
    public Response sample() {
        return Response.ok(sample.png())
                .type("image/png")
                .header("Cache-Control", "public, max-age=3600")
                .build();
    }

    @GET
    @Path("/theory/gaussian")
    @Produces(MediaType.APPLICATION_JSON)
    public GaussianTheory gaussian(@QueryParam("radius") double radius) {
        return filters.gaussianTheory(radius);
    }

    @GET
    @Path("/theory/dither")
    @Produces(MediaType.APPLICATION_JSON)
    public List<DitherPresets.Preset> dither() {
        return DitherPresets.all();
    }

    @POST
    @Path("/render")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("image/png")
    @Blocking
    public Response render(@RestForm("image") FileUpload image, @RestForm("filter") String filter,
            @RestForm("settings") String settingsJson) {
        if (filter == null || filter.isBlank()) {
            throw new IllegalArgumentException("The filter form field is required");
        }
        Map<String, Object> settings = settings(settingsJson);
        FilterSpec spec = catalog.all().stream().filter(item -> item.id().equals(filter)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown filter: " + filter));
        for (String key : settings.keySet()) {
            if (spec.params().stream().noneMatch(param -> param.name().equals(key))) {
                throw new IllegalArgumentException("Unknown setting for " + filter + ": " + key);
            }
        }
        if (image != null && (image.size() == 0 || image.size() > MAX_UPLOAD_BYTES)) {
            throw new IllegalArgumentException("Choose a non-empty image no larger than 10 MiB");
        }
        BufferedImage source = image == null ? sample.create() : read(image.filePath());

        long started = System.nanoTime();
        BufferedImage result = filters.apply(source, filter, settings);
        byte[] png = filters.png(result);
        long millis = (System.nanoTime() - started) / 1_000_000L;

        return Response.ok(png)
                .type("image/png")
                .header("Cache-Control", "no-store")
                .header("X-Render-Millis", millis)
                .header("X-Image-Width", result.getWidth())
                .header("X-Image-Height", result.getHeight())
                .header("X-Output-Bytes", png.length)
                .build();
    }

    private Map<String, Object> settings(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readerFor(new TypeReference<Map<String, Object>>() {
            }).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json);
            if (parsed == null) {
                throw new IllegalArgumentException("The settings field must be a JSON object");
            }
            return parsed;
        }
        catch (IOException exception) {
            throw new IllegalArgumentException("The settings field must be a JSON object", exception);
        }
    }

    private BufferedImage read(java.nio.file.Path path) {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                throw new IllegalArgumentException("The uploaded file is not a supported image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("The uploaded file is not a supported image");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")
                        && !format.equalsIgnoreCase("gif")) {
                    throw new IllegalArgumentException("Choose a PNG, JPEG, or GIF image");
                }
                reader.setInput(input, true, false);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException("The uploaded image is larger than 24 megapixels");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IllegalArgumentException("The uploaded file is not a supported image");
                }
                return ExifOrientation.apply(image, ExifOrientation.read(reader));
            }
            finally {
                reader.dispose();
            }
        }
        catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the uploaded image", exception);
        }
    }
}
