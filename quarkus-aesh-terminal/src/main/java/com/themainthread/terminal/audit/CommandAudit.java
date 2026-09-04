package com.themainthread.terminal.audit;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import org.aesh.command.CommandResult;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CommandAudit {

    private static final Logger LOG = Logger.getLogger(CommandAudit.class);
    private static final int MAX_ENTRIES = 100;
    private static final Pattern SENSITIVE_OPTION = Pattern.compile(
            "(?i)(--(?:password|secret|token)(?:=|\\s+))\\S+");

    private final ConcurrentLinkedDeque<AuditEntry> entries = new ConcurrentLinkedDeque<>();

    public void record(String commandLine, CommandResult result, long executionTimeMs) {
        String safeCommandLine = SENSITIVE_OPTION.matcher(commandLine).replaceAll("$1***");
        entries.addFirst(new AuditEntry(Instant.now(), safeCommandLine, result, executionTimeMs));
        while (entries.size() > MAX_ENTRIES) {
            entries.pollLast();
        }
        LOG.infof("Aesh command completed: command='%s', result=%s, durationMs=%d",
                safeCommandLine, result, executionTimeMs);
    }

    public List<AuditEntry> recent(int limit) {
        return entries.stream().limit(limit).toList();
    }

    public record AuditEntry(Instant timestamp, String commandLine, CommandResult result, long executionTimeMs) {
    }
}
