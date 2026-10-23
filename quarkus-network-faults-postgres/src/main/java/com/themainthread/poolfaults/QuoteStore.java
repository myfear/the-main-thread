package com.themainthread.poolfaults;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class QuoteStore {
    private final EntityManager entityManager;

    public QuoteStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public QuoteView save(String destination) {
        SavedQuote quote = new SavedQuote(destination, new BigDecimal("9.90"));
        entityManager.persist(quote);
        entityManager.flush();
        return view(quote);
    }

    public long count() {
        return entityManager.createQuery("select count(q) from SavedQuote q", Long.class).getSingleResult();
    }

    public List<QuoteView> list() {
        return entityManager.createQuery("from SavedQuote q order by q.destination", SavedQuote.class)
                .getResultList().stream().map(QuoteStore::view).toList();
    }

    private static QuoteView view(SavedQuote quote) {
        return new QuoteView(quote.id, quote.destination, quote.amount, "EUR");
    }

    public record QuoteView(UUID id, String destination, BigDecimal amount, String currency) {
    }
}
