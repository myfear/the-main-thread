package com.themainthread.releasegate;

import com.google.protobuf.Message;
import com.themainthread.releasegate.proto.ReleaseAssessment;
import com.themainthread.releasegate.proto.ReleaseCandidate;
import com.themainthread.releasegate.proto.ReleaseDecision;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
@Path("/releases")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ReleaseResource {

    private final ReleaseGateway gateway;

    ReleaseResource(ReleaseGateway gateway) {
        this.gateway = gateway;
    }

    @POST
    @Path("/evaluate")
    public CompletableFuture<ReleaseResponse> evaluate(@Valid ReleaseRequest request) {
        ReleaseCandidate candidate = ReleaseCandidate.newBuilder()
                .setId(request.id())
                .setChangedFiles(request.changedFiles())
                .setCriticalDependencies(request.criticalDependencies())
                .setForceRiskFailure(request.forceRiskFailure())
                .setAnalysisDelayMillis(request.analysisDelayMillis())
                .build();
        Map<String, Message> payload = new HashMap<>();
        payload.put(PayloadKeys.CANDIDATE, candidate);

        return gateway.evaluate(payload).thenApply(this::toResponse);
    }

    private ReleaseResponse toResponse(Map<String, Message> payload) {
        ReleaseCandidate candidate = (ReleaseCandidate) payload.get(PayloadKeys.CANDIDATE);
        ReleaseAssessment assessment = (ReleaseAssessment) payload.get(PayloadKeys.ASSESSMENT);
        ReleaseDecision decision = (ReleaseDecision) payload.get(PayloadKeys.DECISION);
        return new ReleaseResponse(
                candidate.getId(),
                assessment.getScore(),
                decision.getApproved(),
                decision.getReason(),
                assessment.getProcessedBy(),
                decision.getDecidedBy());
    }

    public record ReleaseRequest(
            @NotBlank String id,
            @Min(0) int changedFiles,
            @Min(0) int criticalDependencies,
            boolean forceRiskFailure,
            @Min(0) int analysisDelayMillis) {
    }

    public record ReleaseResponse(
            String releaseId,
            int riskScore,
            boolean approved,
            String reason,
            String processedBy,
            String decidedBy) {
    }
}
