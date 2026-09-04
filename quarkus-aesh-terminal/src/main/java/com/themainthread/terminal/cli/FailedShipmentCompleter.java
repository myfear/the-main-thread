package com.themainthread.terminal.cli;

import com.themainthread.terminal.dispatch.DispatchService;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;

import io.quarkus.arc.Unremovable;

@Dependent
@Unremovable
public class FailedShipmentCompleter implements OptionCompleter<CompleterInvocation> {

    @Inject
    DispatchService dispatchService;

    @Override
    public void complete(CompleterInvocation invocation) {
        String input = invocation.getGivenCompleteValue();
        dispatchService.failedShipmentIds().stream()
                .filter(id -> input == null || input.isBlank() || id.startsWith(input.toUpperCase()))
                .forEach(invocation::addCompleterValue);
    }
}
