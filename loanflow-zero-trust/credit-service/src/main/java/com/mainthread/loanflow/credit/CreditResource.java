package com.mainthread.loanflow.credit;

import com.mainthread.loanflow.credit.dto.CreditCheckRequest;
import com.mainthread.loanflow.credit.dto.CreditCheckResponse;

import org.jboss.logging.Logger;

import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/internal/credit-checks")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CreditResource {

    private static final Logger LOG = Logger.getLogger(CreditResource.class);

    @Inject
    CreditDecisionService creditDecisionService;

    @Inject
    SecurityIdentity identity;

    @POST
    @PermissionsAllowed("credit_check_run")
    public CreditCheckResponse run(CreditCheckRequest request) {
        CreditCheckResponse response = creditDecisionService.run(request);
        LOG.infof(
                "Credit check loanId=%s band=%s caller=%s",
                response.loanId(),
                response.creditBand(),
                identity.getPrincipal().getName());
        return response;
    }
}