package com.mainthread.loanflow.loan.client;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.mainthread.loanflow.loan.dto.CreditCheckRequest;
import com.mainthread.loanflow.loan.dto.CreditCheckResponse;

import io.quarkus.oidc.client.filter.OidcClientFilter;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/internal/credit-checks")
@RegisterRestClient(configKey = "credit-service")
@OidcClientFilter("internal-calls")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface CreditServiceClient {

    @POST
    CreditCheckResponse run(CreditCheckRequest request);
}