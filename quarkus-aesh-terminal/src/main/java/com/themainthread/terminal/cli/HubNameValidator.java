package com.themainthread.terminal.cli;

import com.themainthread.terminal.dispatch.DispatchService;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import org.aesh.command.validator.OptionValidator;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.command.validator.ValidatorInvocation;

import io.quarkus.arc.Unremovable;

@Dependent
@Unremovable
public class HubNameValidator implements OptionValidator<ValidatorInvocation<String, ?>> {

    @Inject
    DispatchService dispatchService;

    @Override
    public void validate(ValidatorInvocation<String, ?> invocation) throws OptionValidatorException {
        if (!dispatchService.hasHub(invocation.getValue())) {
            throw new OptionValidatorException("Unknown hub '" + invocation.getValue() + "'.");
        }
    }
}
