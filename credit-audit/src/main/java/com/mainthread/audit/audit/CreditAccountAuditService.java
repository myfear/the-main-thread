package com.mainthread.audit.audit;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;

import com.mainthread.audit.domain.CreditAccount;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class CreditAccountAuditService {

    @Inject
    EntityManager em;

    public List<Number> revisions(Long accountId) {
        AuditReader reader = AuditReaderFactory.get(em);
        return reader.getRevisions(CreditAccount.class, accountId);
    }

    public CreditAccount atRevision(Long accountId, Number revision) {
        AuditReader reader = AuditReaderFactory.get(em);
        return reader.find(CreditAccount.class, accountId, revision);
    }

    public AuditSnapshot snapshot(Long accountId, Number revision) {
        AuditReader reader = AuditReaderFactory.get(em);

        CreditAccount account = reader.find(CreditAccount.class, accountId, revision);

        if (account == null) {
            return null;
        }

        Date revisionDate = reader.getRevisionDate(revision);

        return new AuditSnapshot(
                account,
                revision.longValue(),
                revisionDate.toInstant());
    }

    public record AuditSnapshot(
            CreditAccount account,
            long revision,
            Instant timestamp) {
    }
}