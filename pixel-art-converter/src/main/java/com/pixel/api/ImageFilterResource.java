package com.pixel.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.pixel.service.ConversionGate;
import com.pixel.service.FilterChainService;
import com.pixel.service.FilterConfigDTO;
import com.pixel.service.FilterFactory;
import com.pixel.service.ImageDecoder;
import com.pixel.service.ImageLimits;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/filter")
public class ImageFilterResource {
    private static final Logger LOG = Logger.getLogger(ImageFilterResource.class);
    private final FilterChainService chain;
    private final FilterFactory factory;
    private final ImageDecoder decoder;
    private final ImageLimits limits;
    private final ConversionGate gate;

    public ImageFilterResource(FilterChainService chain, FilterFactory factory, ImageDecoder decoder,
            ImageLimits limits, ConversionGate gate) {
        this.chain = chain;
        this.factory = factory;
        this.decoder = decoder;
        this.limits = limits;
        this.gate = gate;
    }

    @POST
    @Path("/pixelate")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("image/png")
    @RunOnVirtualThread
    public Response pixelate(@RestForm("image") FileUpload image,
            @RestForm("blockSize") @DefaultValue("8") int blockSize) {
        if (image == null || image.size() == 0) {
            throw new BadRequestException("A nonempty image is required");
        }
        if (blockSize < 1 || blockSize > limits.maxBlockSize()) {
            throw new BadRequestException("blockSize must be between 1 and " + limits.maxBlockSize());
        }
        try (var permit = gate.enter()) {
            var source = decoder.read(image.uploadedFile(), blockSize);
            var result = chain.applyChain(source, factory.createChain(List.of(
                    new FilterConfigDTO("downsample", Map.of("blockSize", blockSize)),
                    new FilterConfigDTO("dither", Map.of()),
                    new FilterConfigDTO("upsample", Map.of("scale", blockSize)))));
            try (var output = new ByteArrayOutputStream()) {
                if (!ImageIO.write(result, "PNG", output)) {
                    throw new IOException("PNG writer unavailable");
                }
                return Response.ok(output.toByteArray())
                        .header("X-Execution-Virtual", Thread.currentThread().isVirtual())
                        .header("X-Image-Width", result.getWidth())
                        .header("X-Image-Height", result.getHeight()).build();
            } catch (IOException e) {
                LOG.error("PNG encoding failed", e);
                throw new InternalServerErrorException("PNG encoding failed");
            }
        }
    }
}
