package com.mainthread.loanflow.loan;

import java.time.Instant;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import com.mainthread.loanflow.loan.client.CreditServiceClient;
import com.mainthread.loanflow.loan.client.DocumentServiceClient;
import com.mainthread.loanflow.loan.dto.CreditCheckRequest;
import com.mainthread.loanflow.loan.dto.DocumentWriteRequest;
import com.mainthread.loanflow.loan.dto.LoanView;
import com.mainthread.loanflow.loan.dto.SubmitLoanResponse;
import com.mainthread.loanflow.loan.model.LoanApplication;
import com.mainthread.loanflow.loan.model.LoanStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class LoanApplicationService {

    private static final Logger LOG = Logger.getLogger(LoanApplicationService.class);

    private final LoanRepository repository;
    private final LoanAccessPolicy accessPolicy;
    private final CallerContext callerContext;
    private final CreditServiceClient creditServiceClient;
    private final DocumentServiceClient documentServiceClient;

    @Inject
    public LoanApplicationService(
            LoanRepository repository,
            LoanAccessPolicy accessPolicy,
            CallerContext callerContext,
            @RestClient CreditServiceClient creditServiceClient,
            @RestClient DocumentServiceClient documentServiceClient) {
        this.repository = repository;
        this.accessPolicy = accessPolicy;
        this.callerContext = callerContext;
        this.creditServiceClient = creditServiceClient;
        this.documentServiceClient = documentServiceClient;
    }

    public LoanView get(String loanId) {
        LoanApplication loan = findLoan(loanId);
        accessPolicy.checkCanRead(loan);
        LOG.infof(
                "Loan read loanId=%s by %s branch=%s",
                loanId,
                callerContext.principalName(),
                callerContext.branch());
        return toView(loan);
    }

    public SubmitLoanResponse submit(String loanId) {
        LoanApplication loan = findLoan(loanId);
        accessPolicy.checkCanSubmit(loan);
        LOG.infof(
                "Loan submit loanId=%s by %s branch=%s",
                loanId,
                callerContext.principalName(),
                callerContext.branch());

        LOG.infof("Calling credit-service for loanId=%s", loanId);
        var creditCheck = creditServiceClient.run(new CreditCheckRequest(loan.id(), loan.applicantId()));
        LOG.infof("Credit band %s for loanId=%s", creditCheck.creditBand(), loanId);

        LOG.infof("Calling document-service for loanId=%s", loanId);
        documentServiceClient.write(new DocumentWriteRequest(
                loan.id(),
                callerContext.principalName(),
                loan.branch(),
                creditCheck.creditBand(),
                Instant.now()));

        LoanApplication submitted = repository.save(loan.withStatus(LoanStatus.SUBMITTED));
        LOG.infof("Loan submitted loanId=%s creditBand=%s", submitted.id(), creditCheck.creditBand());
        return new SubmitLoanResponse(submitted.id(), submitted.status(), creditCheck.creditBand());
    }

    private LoanApplication findLoan(String loanId) {
        return repository.findById(loanId).orElseThrow(NotFoundException::new);
    }

    private LoanView toView(LoanApplication loan) {
        return new LoanView(loan.id(), loan.branch(), loan.status(), loan.applicantId());
    }
}
