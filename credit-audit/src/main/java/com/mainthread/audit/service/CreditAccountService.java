package com.mainthread.audit.service;

import com.mainthread.audit.domain.CreditAccount;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CreditAccountService {

    @Transactional
    public CreditAccount create(String owner, long limit) {
        CreditAccount account = new CreditAccount();
        account.owner = owner;
        account.creditLimit = limit;
        account.status = CreditAccount.Status.ACTIVE;
        account.persist();
        return account;
    }

    @Transactional
    public CreditAccount updateLimit(Long id, long newLimit) {
        CreditAccount account = CreditAccount.findById(id);
        account.creditLimit = newLimit;
        return account;
    }

    @Transactional
    public CreditAccount suspend(Long id) {
        CreditAccount account = CreditAccount.findById(id);
        account.status = CreditAccount.Status.SUSPENDED;
        return account;
    }
}