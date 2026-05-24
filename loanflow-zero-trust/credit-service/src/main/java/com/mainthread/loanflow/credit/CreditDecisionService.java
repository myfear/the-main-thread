package com.mainthread.loanflow.credit;

import com.mainthread.loanflow.credit.dto.CreditCheckRequest;
import com.mainthread.loanflow.credit.dto.CreditCheckResponse;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CreditDecisionService {

    public CreditCheckResponse run(CreditCheckRequest request) {
        int score = scoreFromLoanId(request.loanId());
        String creditBand = bandForScore(score);
        return new CreditCheckResponse(request.loanId(), creditBand, score);
    }

    private int scoreFromLoanId(String loanId) {
        int hash = Math.abs(loanId.hashCode());
        return 300 + (hash % 501);
    }

    private String bandForScore(int score) {
        if (score >= 700) {
            return "A";
        }
        if (score >= 600) {
            return "B";
        }
        if (score >= 500) {
            return "C";
        }
        return "D";
    }
}
