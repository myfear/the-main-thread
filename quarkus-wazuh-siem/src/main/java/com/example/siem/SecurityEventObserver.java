package com.example.siem;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;

import io.quarkus.security.spi.runtime.AuthenticationFailureEvent;
import io.quarkus.security.spi.runtime.AuthenticationSuccessEvent;
import io.quarkus.security.spi.runtime.AuthorizationFailureEvent;
import io.quarkus.security.spi.runtime.SecurityEvent;
import io.vertx.ext.web.RoutingContext;

import org.jboss.logging.Logger;

@ApplicationScoped
public class SecurityEventObserver {
    private static final Logger LOG = Logger.getLogger(SecurityEventObserver.class);
    private final AuditNormalizer normalizer;
    private final AuditRecorder recorder;
    private final Instance<RoutingContext> currentContext;

    public SecurityEventObserver(AuditNormalizer normalizer, AuditRecorder recorder,
            Instance<RoutingContext> currentContext) {
        this.normalizer = normalizer;
        this.recorder = recorder;
        this.currentContext = currentContext;
    }

    void authenticationFailure(@Observes AuthenticationFailureEvent event) {
        emit(SecurityAuditRecord.EventType.AUTHENTICATION_FAILURE, event);
    }

    void authenticationSuccess(@Observes AuthenticationSuccessEvent event) {
        emit(SecurityAuditRecord.EventType.AUTHENTICATION_SUCCESS, event);
    }

    void authorizationFailure(@Observes AuthorizationFailureEvent event) {
        emit(SecurityAuditRecord.EventType.AUTHORIZATION_FAILURE, event);
    }

    private void emit(SecurityAuditRecord.EventType type, SecurityEvent event) {
        try {
            recorder.record(normalizer.normalize(type, event.getSecurityIdentity(), routingContext(event)));
        } catch (RuntimeException failure) {
            LOG.warn("Security event normalization failed");
        }
    }

    private RoutingContext routingContext(SecurityEvent event) {
        Object context = event.getEventProperties().get(RoutingContext.class.getName());
        if (context instanceof RoutingContext routingContext) {
            return routingContext;
        }
        try {
            return currentContext.isResolvable() ? currentContext.get() : null;
        } catch (ContextNotActiveException unavailable) {
            return null;
        }
    }
}
