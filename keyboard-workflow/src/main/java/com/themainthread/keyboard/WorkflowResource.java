package com.themainthread.keyboard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;

@Path("/")
@Produces(MediaType.TEXT_HTML)
public class WorkflowResource {

    public record View(String step, String purpose, String quantity,
            Map<String, String> errors, String receipt) {
        public String title() {
            if (!errors.isEmpty()) {
                return "Correct " + errors.size() + " errors";
            }
            return switch (step) {
                case "review" -> "Step 2 of 2: Review your request";
                case "done" -> "Demo request submitted";
                default -> "Step 1 of 2: Request details";
            };
        }
    }

    @CheckedTemplate(basePath = "")
    public static class Templates {
        public static native TemplateInstance page(View view);
        public static native TemplateInstance workflow(View view);
    }

    @GET
    public Response start() {
        return render(new View("details", "", "1", Map.of(), ""), false);
    }

    @POST
    @Path("review")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response review(@RestForm String purpose, @RestForm String quantity,
            @HeaderParam("HX-Request") boolean htmx) {
        return render(validate(purpose, quantity, "review"), htmx);
    }

    @POST
    @Path("confirm")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response confirm(@RestForm String purpose, @RestForm String quantity,
            @RestForm String intent, @HeaderParam("HX-Request") boolean htmx) {
        if ("edit".equals(intent)) {
            return render(new View("details", clean(purpose), clean(quantity), Map.of(), ""), htmx);
        }
        // Hidden fields are still untrusted input. Validate the final POST too.
        View checked = validate(purpose, quantity, "done");
        if (checked.errors().isEmpty()) {
            checked = new View("done", checked.purpose(), checked.quantity(), Map.of(),
                    UUID.randomUUID().toString());
        }
        return render(checked, htmx);
    }

    private View validate(String purpose, String quantity, String next) {
        String reason = clean(purpose);
        String count = clean(quantity);
        Map<String, String> errors = new LinkedHashMap<>();
        if (reason.length() < 10 || reason.length() > 200) {
            errors.put("purpose", "Purpose: enter between 10 and 200 characters.");
        }
        if (!count.matches("[1-5]")) {
            errors.put("quantity", "Quantity: enter a whole number from 1 to 5.");
        }
        return new View(errors.isEmpty() ? next : "details", reason, count, errors, "");
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private Response render(View view, boolean htmx) {
        return Response.status(view.errors().isEmpty() ? 200 : 422)
                .entity(htmx ? Templates.workflow(view) : Templates.page(view))
                .header("X-Workflow-Fragment", "true")
                .header("Vary", "HX-Request")
                .header("Cache-Control", "no-store")
                .build();
    }
}
