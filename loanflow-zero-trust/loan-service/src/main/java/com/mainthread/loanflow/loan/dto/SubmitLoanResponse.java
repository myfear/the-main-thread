package com.mainthread.loanflow.loan.dto;

import com.mainthread.loanflow.loan.model.LoanStatus;

public record SubmitLoanResponse(String loanId, LoanStatus status, String creditBand) {
}
