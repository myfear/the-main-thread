package com.themainthread.ledgerlock;

import org.eclipse.microprofile.jwt.JsonWebToken;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/totp")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class TotpResource {

    private final JsonWebToken accessToken;
    private final TotpService totpService;
    private final StepUpTokenService stepUpTokenService;

    public TotpResource(JsonWebToken accessToken, TotpService totpService, StepUpTokenService stepUpTokenService) {
        this.accessToken = accessToken;
        this.totpService = totpService;
        this.stepUpTokenService = stepUpTokenService;
    }

    @POST
    @Path("/enrollment")
    public Response enroll() {
        TotpService.EnrollmentResponse enrollment = totpService.enroll(accessToken.getSubject());
        CacheControl noStore = new CacheControl();
        noStore.setNoStore(true);
        noStore.setNoCache(true);
        return Response.status(Response.Status.CREATED)
                .cacheControl(noStore)
                .header("Pragma", "no-cache")
                .entity(enrollment)
                .build();
    }

    @POST
    @Path("/step-up")
    public StepUpResponse stepUp(@Valid OtpRequest request) {
        String subject = accessToken.getSubject();
        if (!totpService.validate(subject, request.code())) {
            throw new ClientErrorException("The TOTP code is invalid or was already used", Response.Status.UNAUTHORIZED);
        }

        return new StepUpResponse(stepUpTokenService.issueFor(subject), StepUpTokenService.LIFESPAN_SECONDS);
    }

    public record OtpRequest(
            @NotBlank @Pattern(regexp = "[0-9]{6}", message = "must contain exactly six ASCII digits") String code) {
    }

    public record StepUpResponse(String token, long expiresInSeconds) {
    }
}
