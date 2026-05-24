package com.mainthread.loanflow.loan.dto;

import com.mainthread.loanflow.loan.model.LoanStatus;

public record LoanView(String id, String branch, LoanStatus status, String applicantId) {
}
