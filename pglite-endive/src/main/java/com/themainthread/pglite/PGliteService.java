package com.themainthread.pglite;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PGliteService {
    private final PGliteFactory factory;
    private final PGliteConfig config;
    private PGliteDatabase database;

    public PGliteService(PGliteFactory factory, PGliteConfig config) {
        this.factory = factory;
        this.config = config;
    }

    synchronized PGliteDatabase database() {
        if (database == null) {
            database = factory.open(config.dataDirectory());
        }
        return database;
    }

    List<QueryResult> transaction(List<String> statements) {
        return database().transaction(session -> {
            List<QueryResult> results = new ArrayList<>();
            for (String sql : statements) {
                results.add(session.execute(sql));
            }
            return List.copyOf(results);
        });
    }

    @PreDestroy
    synchronized void close() {
        if (database != null) {
            database.close();
        }
    }
}
