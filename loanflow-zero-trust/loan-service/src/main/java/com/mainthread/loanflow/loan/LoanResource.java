package com.mainthread.loanflow.loan;

import com.mainthread.loanflow.loan.dto.LoanView;
import com.mainthread.loanflow.loan.dto.SubmitLoanResponse;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/loans")
@Produces(MediaType.APPLICATION_JSON)
public class LoanResource {

    private final LoanApplicationService loanApplicationService;

    @Inject
    public LoanResource(LoanApplicationService loanApplicationService) {
        this.loanApplicationService = loanApplicationService;
    }

    @GET
    @Path("{loanId}")
    @RolesAllowed({ "loan_officer", "loan_admin" })
    public LoanView get(@PathParam("loanId") String loanId) {
        return loanApplicationService.get(loanId);
    }

    @POST
    @Path("{loanId}/submit")
    @RolesAllowed({ "loan_officer", "loan_admin" })
    public SubmitLoanResponse submit(@PathParam("loanId") String loanId) {
        return loanApplicationService.submit(loanId);
    }
}
