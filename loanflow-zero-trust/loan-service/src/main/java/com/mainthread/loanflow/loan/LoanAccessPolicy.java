package com.mainthread.loanflow.loan;

import com.mainthread.loanflow.loan.model.LoanApplication;
import com.mainthread.loanflow.loan.model.LoanStatus;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.WebApplicationException;

@ApplicationScoped
public class LoanAccessPolicy {

    private static final Logger LOG = Logger.getLogger(LoanAccessPolicy.class);

    private final CallerContext callerContext;

    public LoanAccessPolicy(CallerContext callerContext) {
        this.callerContext = callerContext;
    }

    public void checkCanRead(LoanApplication loan) {
        if (callerContext.hasRole("loan_admin")) {
            return;
        }

        String branch = callerContext.branch();
        if (!callerContext.hasRole("loan_officer") || !loan.branch().equals(branch)) {
            LOG.warnf(
                    "Loan access denied loanId=%s caller=%s callerBranch=%s loanBranch=%s",
                    loan.id(),
                    callerContext.principalName(),
                    branch,
                    loan.branch());
            throw new ForbiddenException();
        }
    }

    public void checkCanSubmit(LoanApplication loan) {
        checkCanRead(loan);
        if (loan.status() != LoanStatus.DRAFT) {
            LOG.warnf(
                    "Loan submit rejected loanId=%s caller=%s status=%s",
                    loan.id(),
                    callerContext.principalName(),
                    loan.status());
            throw new WebApplicationException("Loan is not in DRAFT status", 409);
        }
    }
}
