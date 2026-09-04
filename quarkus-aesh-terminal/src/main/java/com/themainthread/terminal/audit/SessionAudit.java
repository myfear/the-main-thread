package com.themainthread.terminal.audit;

import io.quarkus.aesh.runtime.AeshSessionEvent;
import io.quarkus.aesh.runtime.SessionClosed;
import io.quarkus.aesh.runtime.SessionOpened;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;

import org.jboss.logging.Logger;

@ApplicationScoped
public class SessionAudit {

    private static final Logger LOG = Logger.getLogger(SessionAudit.class);

    void opened(@ObservesAsync @SessionOpened AeshSessionEvent event) {
        LOG.infof("Aesh session opened: id=%s, transport=%s, timestamp=%s",
                event.sessionId(), event.transport(), event.timestamp());
    }

    void closed(@ObservesAsync @SessionClosed AeshSessionEvent event) {
        LOG.infof("Aesh session closed: id=%s, transport=%s, timestamp=%s",
                event.sessionId(), event.transport(), event.timestamp());
    }
}
