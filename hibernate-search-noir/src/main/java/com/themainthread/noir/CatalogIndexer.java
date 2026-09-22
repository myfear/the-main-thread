package com.themainthread.noir;

import org.hibernate.search.mapper.orm.mapping.SearchMapping;
import org.jboss.logging.Logger;

import com.themainthread.noir.model.Book;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CatalogIndexer {

    private static final Logger log = Logger.getLogger(CatalogIndexer.class);

    @Inject
    SearchMapping searchMapping;

    @Transactional
    void onStart(@Observes StartupEvent ev) throws InterruptedException {
        if (Book.count() == 0) {
            return;
        }
        log.info("SQL import is invisible to Hibernate Search; mass-indexing the catalog");
        searchMapping.scope(Object.class)
                .massIndexer()
                .startAndWait();
    }
}
