package com.themainthread.timetraveler;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import org.hibernate.Session;
import org.hibernate.audit.AuditEntry;
import org.hibernate.audit.AuditLog;
import org.hibernate.audit.AuditLogFactory;

@ApplicationScoped
public class AccountHolderService {

    private final Session session;

    public AccountHolderService(Session session) {
        this.session = session;
    }

    @Transactional
    public AccountHolder create(String externalId, String fullName, String email, KycStatus kycStatus) {
        AccountHolder holder = new AccountHolder(externalId, fullName, email, kycStatus);
        session.persist(holder);
        return holder;
    }

    @Transactional
    public AccountHolder update(Long id, String fullName, String email, KycStatus kycStatus) {
        AccountHolder holder = session.find(AccountHolder.class, id);
        if (holder == null) {
            throw new NotFoundException("No account holder with id " + id);
        }
        holder.update(fullName, email, kycStatus);
        return holder;
    }

    public List<AuditEntry<AccountHolder>> getHistory(Long id) {
        try (AuditLog auditLog = AuditLogFactory.create(session)) {
            return auditLog.getHistory(AccountHolder.class, id);
        }
    }

}
