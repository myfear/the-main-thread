package com.themainthread.terminal.cli;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.themainthread.terminal.audit.CommandAudit;

import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

@CommandDefinition(name = "audit", description = "Show recent command executions")
public class AuditCommand implements Command<CommandInvocation> {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    @Option(name = "limit", shortName = 'n', description = "Maximum entries", defaultValue = "10")
    int limit;

    @Inject
    CommandAudit commandAudit;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        var entries = commandAudit.recent(Math.max(1, Math.min(limit, 50)));
        if (entries.isEmpty()) {
            invocation.println("No commands recorded in this process.");
            return CommandResult.SUCCESS;
        }

        invocation.println(String.format("%-20s %-8s %8s %s", "TIME", "RESULT", "MS", "COMMAND"));
        entries.forEach(entry -> invocation.println(String.format(
                "%-20s %-8s %8d %s",
                TIMESTAMP.format(entry.timestamp()),
                entry.result().isSuccess() ? "SUCCESS" : "FAILURE",
                entry.executionTimeMs(),
                entry.commandLine())));
        return CommandResult.SUCCESS;
    }
}
