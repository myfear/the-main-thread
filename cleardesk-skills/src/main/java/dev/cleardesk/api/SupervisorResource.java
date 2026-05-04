package dev.cleardesk.api;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import dev.cleardesk.routing.RoutingTrace;
import dev.cleardesk.routing.Specialist;
import dev.cleardesk.supervisor.SupervisorBaselineAssistant;
import dev.cleardesk.supervisor.SupervisorWithSkillsAssistant;

/**
 * Entry point for the ClearDesk supervisor (skills on vs off).
 */
@Path("/clear-desk")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SupervisorResource {

    @Inject
    SupervisorWithSkillsAssistant withSkillsAssistant;

    @Inject
    SupervisorBaselineAssistant baselineAssistant;

    @Inject
    RoutingTrace routingTrace;

    @POST
    @Path("/chat")
    public ClearDeskChatResponse chat(ClearDeskChatRequest request) {
        if (request.prompt == null || request.prompt.isBlank()) {
            throw new IllegalArgumentException("prompt is required");
        }
        String memoryId = request.memoryId != null && !request.memoryId.isBlank()
                ? request.memoryId
                : UUID.randomUUID().toString();

        String reply = request.skillsEnabled
                ? withSkillsAssistant.handle(memoryId, request.prompt)
                : baselineAssistant.handle(memoryId, request.prompt);

        Specialist specialist = routingTrace.getLastRoutedSpecialist();
        String routed = specialist != null ? specialist.name() : null;

        return new ClearDeskChatResponse(reply, memoryId, request.skillsEnabled, routed);
    }

    /**
     * Runs the same prompt twice (baseline vs skills) so you can compare routing side-by-side in dev.
     */
    @GET
    @Path("/compare")
    @Produces(MediaType.APPLICATION_JSON)
    public CompareResponse compare(@QueryParam("prompt") String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt query parameter is required");
        }
        String memoryBaseline = UUID.randomUUID().toString();
        String memorySkills = UUID.randomUUID().toString();

        routingTrace.reset();
        String baselineReply = baselineAssistant.handle(memoryBaseline, prompt);
        Specialist baselineSpecialist = routingTrace.getLastRoutedSpecialist();

        routingTrace.reset();
        String skillsReply = withSkillsAssistant.handle(memorySkills, prompt);
        Specialist skillsSpecialist = routingTrace.getLastRoutedSpecialist();

        return new CompareResponse(
                prompt,
                new Side(baselineReply, memoryBaseline, specialistName(baselineSpecialist)),
                new Side(skillsReply, memorySkills, specialistName(skillsSpecialist)));
    }

    private static String specialistName(Specialist s) {
        return s != null ? s.name() : null;
    }

    public record CompareResponse(String prompt, Side baseline, Side withSkills) {
    }

    public record Side(String reply, String memoryId, String routedSpecialist) {
    }
}
