package com.themainthread.terminal.cli;

import org.aesh.command.option.Option;

public class HubTarget {

    @Option(
            name = "hub",
            shortName = 'h',
            description = "Hub name",
            required = true,
            completer = HubNameCompleter.class,
            validator = HubNameValidator.class)
    String name;

    String name() {
        return name;
    }
}
