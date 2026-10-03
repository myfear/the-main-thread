package com.themainthread.barcode;

import java.util.ArrayList;
import java.util.List;

import io.smallrye.common.annotation.Blocking;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Path("/barcodes")
public class BarcodeResource {
    private final BarcodeService service;

    public BarcodeResource(BarcodeService service) {
        this.service = service;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<FormatDescription> formats() {
        List<FormatDescription> descriptions = new ArrayList<>();
        for (BarcodeType type : BarcodeType.values()) {
            descriptions.add(new FormatDescription(type.id(), type.label(), type.example(), type.goodFit(),
                    type.constraint(), type.width(), type.height()));
        }
        return descriptions;
    }

    @GET
    @Path("/{type}")
    @Produces("image/png")
    @Blocking
    public Response generate(@PathParam("type") String typeId, @QueryParam("value") String value,
            @QueryParam("width") String width, @QueryParam("height") String height, @QueryParam("ecc") String ecc) {
        return render(typeId, value, width, height, ecc);
    }

    @POST
    @Path("/{type}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces("image/png")
    @Blocking
    public Response generateFromBody(@PathParam("type") String typeId, String value,
            @QueryParam("width") String width, @QueryParam("height") String height, @QueryParam("ecc") String ecc) {
        return render(typeId, value, width, height, ecc);
    }

    private Response render(String typeId, String value, String width, String height, String ecc) {
        BarcodeType type = BarcodeType.fromId(typeId);
        BarcodeService.GeneratedBarcode barcode = service.generate(type, value, width, height, ecc);
        return Response.ok(barcode.png(), "image/png")
                .header("Cache-Control", "no-store")
                .header("X-Barcode-Width", barcode.width())
                .header("X-Barcode-Height", barcode.height())
                .build();
    }

    @Provider
    public static class InputErrorMapper implements ExceptionMapper<BarcodeInputException> {
        @Override
        public Response toResponse(BarcodeInputException exception) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type("application/problem+json")
                    .header("Cache-Control", "no-store")
                    .entity(new InputProblem("urn:barcode-lab:invalid-input", "Invalid barcode request", 400,
                            exception.getMessage(), exception.field()))
                    .build();
        }
    }

    public record InputProblem(String type, String title, int status, String detail, String field) {
    }

    public record FormatDescription(String id, String label, String example, String goodFit, String constraint,
            int width, int height) {
    }
}
