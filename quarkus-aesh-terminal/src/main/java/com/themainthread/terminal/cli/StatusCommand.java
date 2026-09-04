package com.themainthread.terminal.cli;

import com.themainthread.terminal.dispatch.DispatchService;

import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;

@CommandDefinition(name = "status", description = "Show every dispatch hub")
public class StatusCommand implements Command<CommandInvocation> {

    @Inject
    DispatchService dispatchService;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        invocation.println(String.format("%-10s %-10s %8s %7s %8s", "HUB", "STATE", "QUEUED", "FAILED", "TRAFFIC"));
        dispatchService.hubs().forEach(hub -> invocation.println(String.format(
                "%-10s %-10s %8d %7d %8s",
                hub.name(),
                hub.state(),
                hub.queued(),
                hub.failed(),
                hub.acceptingTraffic() ? "OPEN" : "STOPPED")));
        return CommandResult.SUCCESS;
    }
}
