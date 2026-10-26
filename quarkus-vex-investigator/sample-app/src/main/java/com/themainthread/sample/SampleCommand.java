package com.themainthread.sample;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@TopCommand
@Command(name = "inventory-fixture", mixinStandardHelpOptions = true)
public class SampleCommand implements Runnable {
    @Spec CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getOut().println("Disposable VEX inventory fixture. No interpolation is performed.");
    }
}
