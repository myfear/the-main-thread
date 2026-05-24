package com.mainthread.loanflow.loan.model;

public record LoanApplication(String id, String branch, LoanStatus status, String applicantId) {

    public LoanApplication withStatus(LoanStatus newStatus) {
        return new LoanApplication(id, branch, newStatus, applicantId);
    }
}
