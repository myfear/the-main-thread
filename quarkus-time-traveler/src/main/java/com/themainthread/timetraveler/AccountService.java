package com.themainthread.timetraveler;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.audit.AuditLog;
import org.hibernate.audit.AuditLogFactory;

@ApplicationScoped
public class AccountService {

    private final Session session;
    private final SessionFactory sessionFactory;

    public AccountService(Session session, SessionFactory sessionFactory) {
        this.session = session;
        this.sessionFactory = sessionFactory;
    }

    @Transactional
    public Account create(String accountNumber, BigDecimal openingBalance) {
        Account account = new Account(accountNumber, openingBalance, AccountStatus.ACTIVE);
        session.persist(account);
        return account;
    }

    @Transactional
    public Account getCurrent(Long id) {
        Account account = session.find(Account.class, id);
        if (account == null) {
            throw new NotFoundException("No account with id " + id);
        }
        return account;
    }

    @Transactional
    public Account getSnapshot(Long id, Instant asOf) {
        try (AuditLog auditLog = AuditLogFactory.create(session)) {
            Object changesetId = auditLog.getChangesetId(asOf);
            try (Session historicalSession = sessionFactory.withOptions().atChangeset(changesetId).openSession()) {
                Account account = historicalSession.find(Account.class, id);
                if (account == null) {
                    throw new NotFoundException("No account with id " + id + " at " + asOf);
                }
                return account;
            }
        }
    }

    @Transactional
    public Account changeBalance(Long id, BigDecimal balance, AccountStatus status) {
        Account account = session.find(Account.class, id);
        if (account == null) {
            throw new NotFoundException("No account with id " + id);
        }
        account.changeBalance(balance, status);
        return account;
    }
}
