package com.mainthread.loanflow.credit.dto;

public record CreditCheckResponse(String loanId, String creditBand, int score) {
}
