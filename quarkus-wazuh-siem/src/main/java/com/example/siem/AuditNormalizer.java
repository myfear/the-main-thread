package com.example.siem;

import java.time.Instant;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;

@ApplicationScoped
public class AuditNormalizer {
    private static final String REQUEST_ID = AuditNormalizer.class.getName() + ".request-id";

    public SecurityAuditRecord normalize(SecurityAuditRecord.EventType type, SecurityIdentity identity,
            RoutingContext context) {
        String principal = null;
        if (type != SecurityAuditRecord.EventType.AUTHENTICATION_FAILURE
                && identity != null && !identity.isAnonymous() && identity.getPrincipal() != null) {
            principal = bounded(identity.getPrincipal().getName(), 128);
        }
        var outcome = switch (type) {
            case AUTHENTICATION_FAILURE -> SecurityAuditRecord.Outcome.FAILURE;
            case AUTHORIZATION_FAILURE -> SecurityAuditRecord.Outcome.DENIED;
            default -> SecurityAuditRecord.Outcome.SUCCESS;
        };
        String source = null;
        String method = null;
        String path = null;
        String requestId = UUID.randomUUID().toString();
        if (context != null) {
            synchronized (context) {
                String existing = context.get(REQUEST_ID);
                if (existing == null) {
                    context.put(REQUEST_ID, requestId);
                    context.response().putHeader("X-Audit-Request-ID", requestId);
                } else {
                    requestId = existing;
                }
            }
            var request = context.request();
            if (request.remoteAddress() != null) {
                source = bounded(request.remoteAddress().hostAddress(), 64);
            }
            method = request.method().name();
            // Retain only the two known routes; arbitrary URLs can contain personal data.
            path = switch (request.path()) {
                case "/api/profile" -> "/api/profile";
                case "/api/admin/export" -> "/api/admin/export";
                default -> "<other>";
            };
        }
        return new SecurityAuditRecord(1, UUID.randomUUID().toString(), Instant.now().toString(),
                "quarkus-siem-demo", type, outcome, principal, source, method, path, requestId);
    }

    private static String bounded(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }
}
