package com.mainthread.loanflow.loan;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class LoanDataSeeder {

    private final LoanRepository repository;

    @Inject
    public LoanDataSeeder(LoanRepository repository) {
        this.repository = repository;
    }

    void onStart(@Observes StartupEvent event) {
        if (repository.isEmpty()) {
            repository.seedDefaults();
        }
    }
}
