package com.themainthread.fernbank;

import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

@ApplicationScoped
public class DestinationTeamGuardrail implements ToolInputGuardrail {

    private static final Logger LOG = Logger.getLogger(DestinationTeamGuardrail.class);

    private final SecurityIdentity identity;

    DestinationTeamGuardrail(SecurityIdentity identity) {
        this.identity = identity;
    }

    @Override
    public void apply(ToolInputContext context) {
        String destinationTeam = context.getArguments().getString("destinationTeam");
        if (destinationTeam == null || destinationTeam.isBlank()) {
            throw new ToolCallException("destinationTeam is required");
        }
        if (!identity.hasRole(destinationTeam)) {
            LOG.warnf(
                    "tool_argument_denied principal=%s tool=%s destination_team=%s request_id=%s",
                    identity.getPrincipal().getName(),
                    context.getTool().name(),
                    destinationTeam,
                    context.getRequestId());
            throw new ToolCallException(
                    "Principal %s cannot generate documents for team %s"
                            .formatted(identity.getPrincipal().getName(), destinationTeam));
        }
    }
}
