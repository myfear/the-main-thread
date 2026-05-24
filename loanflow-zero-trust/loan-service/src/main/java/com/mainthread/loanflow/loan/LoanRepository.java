package com.mainthread.loanflow.loan;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.mainthread.loanflow.loan.model.LoanApplication;
import com.mainthread.loanflow.loan.model.LoanStatus;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LoanRepository {

    private final Map<String, LoanApplication> loans = new ConcurrentHashMap<>();

    public Optional<LoanApplication> findById(String id) {
        return Optional.ofNullable(loans.get(id));
    }

    public LoanApplication save(LoanApplication loan) {
        loans.put(loan.id(), loan);
        return loan;
    }

    public boolean isEmpty() {
        return loans.isEmpty();
    }

    public void seedDefaults() {
        save(new LoanApplication("LN-100", "berlin", LoanStatus.DRAFT, "APP-100"));
        save(new LoanApplication("LN-200", "hamburg", LoanStatus.DRAFT, "APP-200"));
        save(new LoanApplication("LN-300", "berlin", LoanStatus.SUBMITTED, "APP-300"));
    }
}
