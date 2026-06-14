package dev.verdictiq.service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.verdictiq.model.PanelVerdict;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VerdictStore {

    private final ConcurrentHashMap<String, PanelVerdict> store = new ConcurrentHashMap<>();

    public void put(PanelVerdict verdict) {
        store.put(verdict.id(), verdict);
    }

    public Optional<PanelVerdict> find(String id) {
        return Optional.ofNullable(store.get(id));
    }
}
