package com.themainthread.terminal.cli;

import com.themainthread.terminal.audit.CommandAudit;
import com.themainthread.terminal.dispatch.DispatchService;
import com.themainthread.terminal.dispatch.DispatchService.OperationResult;

import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;

@CommandDefinition(
        name = "hub",
        description = "Operate one dispatch hub",
        groupCommands = {
                FailuresCommand.class,
                RetryCommand.class,
                DrainCommand.class,
                ResumeCommand.class
        })
public class HubCommand implements Command<CommandInvocation> {

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        invocation.println("Choose a subcommand: failures, retry, drain, or resume.");
        return CommandResult.SUCCESS;
    }
}

@CommandDefinition(name = "failures", description = "List failed shipments")
class FailuresCommand implements Command<CommandInvocation> {

    @Mixin
    HubTarget target;

    @Inject
    DispatchService dispatchService;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        var failures = dispatchService.failures(target.name());
        if (failures.isEmpty()) {
            invocation.println("No failed shipments.");
            return CommandResult.SUCCESS;
        }

        invocation.println(String.format("%-10s %-18s %8s %-16s", "SHIPMENT", "REASON", "ATTEMPTS", "NEXT ROUTE"));
        failures.forEach(failure -> invocation.println(String.format(
                "%-10s %-18s %8d %-16s",
                failure.id(), failure.reason(), failure.attempts(), failure.nextRoute())));
        return CommandResult.SUCCESS;
    }
}

@CommandDefinition(name = "retry", description = "Preview or queue one retry")
class RetryCommand implements Command<CommandInvocation> {

    @Mixin
    HubTarget target;

    @Argument(description = "Shipment ID", required = true, completer = FailedShipmentCompleter.class)
    String shipmentId;

    @Option(name = "dry-run", description = "Show the retry plan without changing state", hasValue = false)
    boolean dryRun;

    @Option(name = "confirm", description = "Repeat the shipment ID to authorize the retry")
    String confirmation;

    @Inject
    DispatchService dispatchService;

    @Inject
    CommandAudit commandAudit;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        long started = System.nanoTime();
        String hubName = target.name();
        OperationResult result = dispatchService.retry(hubName, shipmentId, dryRun, confirmation);
        invocation.println(result.message());
        CommandResult commandResult = result.successful() ? CommandResult.SUCCESS : CommandResult.FAILURE;
        commandAudit.record(
                "retry " + shipmentId + " in " + hubName + (dryRun ? " (dry-run)" : ""),
                commandResult,
                (System.nanoTime() - started) / 1_000_000);
        return commandResult;
    }
}

@CommandDefinition(name = "drain", description = "Stop new traffic for the selected hub")
class DrainCommand implements Command<CommandInvocation> {

    @Mixin
    HubTarget target;

    @Option(name = "confirm", description = "Repeat the hub name to authorize the drain", required = true)
    String confirmation;

    @Inject
    DispatchService dispatchService;

    @Inject
    CommandAudit commandAudit;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        long started = System.nanoTime();
        String hubName = target.name();
        OperationResult result = dispatchService.drain(hubName, confirmation);
        invocation.println(result.message());
        CommandResult commandResult = result.successful() ? CommandResult.SUCCESS : CommandResult.FAILURE;
        commandAudit.record(
                "drain " + hubName,
                commandResult,
                (System.nanoTime() - started) / 1_000_000);
        return commandResult;
    }
}

@CommandDefinition(name = "resume", description = "Resume traffic for the selected hub")
class ResumeCommand implements Command<CommandInvocation> {

    @Mixin
    HubTarget target;

    @Option(name = "confirm", description = "Repeat the hub name to authorize the resume", required = true)
    String confirmation;

    @Inject
    DispatchService dispatchService;

    @Inject
    CommandAudit commandAudit;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        long started = System.nanoTime();
        String hubName = target.name();
        OperationResult result = dispatchService.resume(hubName, confirmation);
        invocation.println(result.message());
        CommandResult commandResult = result.successful() ? CommandResult.SUCCESS : CommandResult.FAILURE;
        commandAudit.record(
                "resume " + hubName,
                commandResult,
                (System.nanoTime() - started) / 1_000_000);
        return commandResult;
    }
}
