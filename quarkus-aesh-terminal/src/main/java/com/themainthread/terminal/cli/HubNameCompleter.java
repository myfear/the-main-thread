package com.themainthread.terminal.cli;

import com.themainthread.terminal.dispatch.DispatchService;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;

import io.quarkus.arc.Unremovable;

@Dependent
@Unremovable
public class HubNameCompleter implements OptionCompleter<CompleterInvocation> {

    @Inject
    DispatchService dispatchService;

    @Override
    public void complete(CompleterInvocation invocation) {
        String input = invocation.getGivenCompleteValue();
        dispatchService.hubNames().stream()
                .filter(name -> input == null || input.isBlank() || name.startsWith(input))
                .forEach(invocation::addCompleterValue);
    }
}
